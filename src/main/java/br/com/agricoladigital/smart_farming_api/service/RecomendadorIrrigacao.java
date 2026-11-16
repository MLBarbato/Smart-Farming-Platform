package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.*;
import br.com.agricoladigital.smart_farming_api.service.CalculadoraIrrigacao.Calculo;
import br.com.agricoladigital.smart_farming_api.service.CalculadoraIrrigacao.Entrada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reúne os dados já gravados de uma fazenda (leituras, estação, previsão, cultura em cultivo), pede o
 * cálculo e grava a recomendação de cada talhão com plantio EM_CULTIVO. Só lê o banco: nenhuma chamada
 * externa. Uma nova recomendação só é gravada quando o resultado muda (ou quando os dados que faltam
 * mudam) ou quando a anterior venceu; assim o histórico não se enche de repetições.
 *
 * <p>A previsão é diária, em datas locais da fazenda; "hoje e amanhã" é aproximado pela data UTC e a
 * chuva é a soma dos dois dias.
 */
@Slf4j
@Service
public class RecomendadorIrrigacao {
    /** Quantas recomendações foram calculadas e quantas gravadas. */
    public record ResumoCalculo(int calculadas, int gravadas) {
    }

    private final FazendaRepository fazendaRepository;
    private final TalhaoRepository talhaoRepository;
    private final PlantioRepository plantioRepository;
    private final SensorRepository sensorRepository;
    private final EstacaoMeteorologicaRepository estacaoRepository;
    private final PrevisaoTempoRepository previsaoRepository;
    private final LeituraRepository leituraRepository;
    private final RecomendacaoIrrigacaoRepository recomendacaoRepository;
    private final CalculadoraIrrigacao calculadora;
    private final Duration validadePrevisao;
    private final Duration validadeRecomendacao;
    private final int janelaUmidadeHoras;
    private final Clock clock;

    @Autowired
    public RecomendadorIrrigacao(
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            PrevisaoTempoRepository previsaoRepository,
            LeituraRepository leituraRepository,
            RecomendacaoIrrigacaoRepository recomendacaoRepository,
            CalculadoraIrrigacao calculadora,
            @Value("${app.previsao.validade-horas:12}") long validadePrevisaoHoras,
            @Value("${app.irrigacao.validade-horas:6}") long validadeRecomendacaoHoras,
            @Value("${app.irrigacao.janela-umidade-horas:3}") int janelaUmidadeHoras
    ) {
        this(fazendaRepository, talhaoRepository, plantioRepository, sensorRepository, estacaoRepository,
                previsaoRepository, leituraRepository, recomendacaoRepository, calculadora, validadePrevisaoHoras,
                validadeRecomendacaoHoras, janelaUmidadeHoras, Clock.systemUTC());
    }

    RecomendadorIrrigacao(
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            PrevisaoTempoRepository previsaoRepository,
            LeituraRepository leituraRepository,
            RecomendacaoIrrigacaoRepository recomendacaoRepository,
            CalculadoraIrrigacao calculadora,
            long validadePrevisaoHoras,
            long validadeRecomendacaoHoras,
            int janelaUmidadeHoras,
            Clock clock
    ) {
        if (validadePrevisaoHoras < 1 || validadeRecomendacaoHoras < 1 || janelaUmidadeHoras < 1 || janelaUmidadeHoras > 48) {
            throw new IllegalArgumentException(
                    "app.irrigacao.*: validade-horas positiva e janela-umidade-horas entre 1 e 48 (e previsao.validade-horas positiva).");
        }
        this.fazendaRepository = fazendaRepository;
        this.talhaoRepository = talhaoRepository;
        this.plantioRepository = plantioRepository;
        this.sensorRepository = sensorRepository;
        this.estacaoRepository = estacaoRepository;
        this.previsaoRepository = previsaoRepository;
        this.leituraRepository = leituraRepository;
        this.recomendacaoRepository = recomendacaoRepository;
        this.calculadora = calculadora;
        this.validadePrevisao = Duration.ofHours(validadePrevisaoHoras);
        this.validadeRecomendacao = Duration.ofHours(validadeRecomendacaoHoras);
        this.janelaUmidadeHoras = janelaUmidadeHoras;
        this.clock = clock;
    }

    @Transactional
    public ResumoCalculo calcularFazenda(Long fazendaId) {
        if (fazendaRepository.findById(fazendaId).isEmpty()) {
            return new ResumoCalculo(0, 0);
        }
        List<Plantio> emCultivo = plantioRepository.buscarPorFazendaEStatus(fazendaId, StatusPlantio.EM_CULTIVO);
        if (emCultivo.isEmpty()) {
            return new ResumoCalculo(0, 0);
        }
        LocalDateTime agora = LocalDateTime.now(clock).withNano(0);
        LocalDateTime de = agora.minusHours(janelaUmidadeHoras);

        List<Sensor> sensoresTalhao = sensorRepository.listarPorFazenda(fazendaId);
        List<Long> soloDaEstacao = estacaoRepository.buscarDaFazenda(fazendaId)
                .filter(e -> e.getDispositivo().getStatus() == StatusDispositivo.ATIVO)
                .map(e -> sensorRepository.listarDaEstacao(e.getId()).stream()
                        .filter(s -> ativoDeSolo(s)).map(Sensor::getId).toList())
                .orElse(List.of());

        List<PrevisaoTempo> previsao = previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(fazendaId);
        boolean previsaoAtual = !previsao.isEmpty()
                && Duration.between(previsao.get(0).getConsultadaEm(), agora).compareTo(validadePrevisao) <= 0;
        LocalDate hoje = agora.toLocalDate();
        List<PrevisaoTempo> proximos = previsaoAtual ? previsao.stream()
                .filter(p -> !p.getDataPrevisao().isBefore(hoje) && !p.getDataPrevisao().isAfter(hoje.plusDays(1)))
                .toList() : List.of();
        Double chuva = soma(proximos);
        Double temperaturaMaxima = proximos.stream().map(PrevisaoTempo::getTemperaturaMaxima)
                .filter(Objects::nonNull).max(Double::compare).orElse(null);
        PrevisaoTempo referencia = proximos.isEmpty() ? null : proximos.get(0);

        Map<Long, RecomendacaoIrrigacao> anteriores = recomendacaoRepository.ultimasDaFazenda(fazendaId).stream()
                .collect(Collectors.toMap(r -> r.getTalhao().getId(), r -> r, (a, b) -> a.getId() > b.getId() ? a : b));

        int calculadas = 0;
        int gravadas = 0;
        for (Plantio plantio : emCultivo) {
            Talhao talhao = plantio.getTalhao();
            List<Long> soloDoTalhao = sensoresTalhao.stream()
                    .filter(s -> talhao.getId().equals(s.getDispositivo().getTalhao().getId()) && ativoDeSolo(s))
                    .map(Sensor::getId).toList();
            OrigemUmidade origem = soloDoTalhao.isEmpty() ? OrigemUmidade.ESTACAO : OrigemUmidade.SENSOR_TALHAO;
            List<Long> fonte = soloDoTalhao.isEmpty() ? soloDaEstacao : soloDoTalhao;
            Double umidade = fonte.isEmpty() ? null : leituraRepository.mediaNoIntervalo(fonte, de, agora);

            Calculo calculo = calculadora.calcular(new Entrada(
                    plantio.getCultura(), umidade, umidade == null ? null : origem, previsaoAtual, chuva,
                    temperaturaMaxima, janelaUmidadeHoras));
            calculadas++;
            if (precisaGravar(anteriores.get(talhao.getId()), calculo, agora)) {
                recomendacaoRepository.save(new RecomendacaoIrrigacao(talhao, referencia, agora,
                        agora.plus(validadeRecomendacao), calculo.situacao(), calculo.volumeMm(),
                        calculo.justificativa(), calculo.insumos()));
                gravadas++;
            }
        }
        return new ResumoCalculo(calculadas, gravadas);
    }

    private static boolean ativoDeSolo(Sensor sensor) {
        return sensor.getTipoSensor() == VariavelMonitorada.UMIDADE_SOLO
                && sensor.getDispositivo().getStatus() == StatusDispositivo.ATIVO;
    }

    private static Double soma(List<PrevisaoTempo> dias) {
        List<Double> valores = dias.stream().map(PrevisaoTempo::getPrecipitacaoPrevista).filter(Objects::nonNull).toList();
        return valores.isEmpty() ? null : valores.stream().mapToDouble(Double::doubleValue).sum();
    }

    private static boolean precisaGravar(RecomendacaoIrrigacao anterior, Calculo calculo, LocalDateTime agora) {
        if (anterior == null || anterior.getSituacao() != calculo.situacao() || !agora.isBefore(anterior.getValidoAte())) {
            return true;
        }
        if (calculo.situacao() == SituacaoIrrigacao.SEM_DADOS_SUFICIENTES) {
            return !anterior.listaDadosFaltantes().equals(calculo.insumos().dadosFaltantes());
        }
        return false;
    }
}
