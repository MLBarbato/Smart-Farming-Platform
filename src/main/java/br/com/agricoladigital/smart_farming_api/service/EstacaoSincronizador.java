package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.client.ClimaClient;
import br.com.agricoladigital.smart_farming_api.client.MedicaoHoraria;
import br.com.agricoladigital.smart_farming_api.entity.EstacaoMeteorologica;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.OrigemLeitura;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traz do provedor as condições horárias e grava, como leituras {@code EXTERNA}, só as horas que
 * o sensor ainda não tem: a mesma rotina cobre a carga inicial (sensores sem leitura), o uso
 * contínuo e a recuperação de lacunas (a janela pedida vai da última leitura até agora). A
 * chamada externa acontece fora de transação.
 */
@Slf4j
@Component
public class EstacaoSincronizador {
    /** Horas de histórico trazidas quando algum sensor ainda não tem leituras. */
    static final int HORAS_MINIMAS = 3;

    private final ClimaClient clima;
    private final SensorRepository sensorRepository;
    private final LeituraRepository leituraRepository;
    private final LeituraService leituraService;
    private final EstacaoCadastro cadastro;
    private final int historicoInicialHoras;
    private final int horasMaximasDeRecuperacao;
    private final Clock clock;

    @Autowired
    public EstacaoSincronizador(
            ClimaClient clima,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            LeituraService leituraService,
            EstacaoCadastro cadastro,
            @Value("${app.estacao.historico-inicial-horas:72}") int historicoInicialHoras,
            @Value("${app.estacao.recuperacao-maxima-horas:168}") int horasMaximasDeRecuperacao
    ) {
        this(clima, sensorRepository, leituraRepository, leituraService, cadastro, historicoInicialHoras,
                horasMaximasDeRecuperacao, Clock.systemUTC());
    }

    EstacaoSincronizador(
            ClimaClient clima,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            LeituraService leituraService,
            EstacaoCadastro cadastro,
            int historicoInicialHoras,
            int horasMaximasDeRecuperacao,
            Clock clock
    ) {
        if (historicoInicialHoras < HORAS_MINIMAS || historicoInicialHoras > 2208
                || horasMaximasDeRecuperacao < HORAS_MINIMAS || horasMaximasDeRecuperacao > 2208) {
            throw new IllegalArgumentException(
                    "app.estacao.*: horas devem estar entre " + HORAS_MINIMAS + " e 2208 (92 dias).");
        }
        this.clima = clima;
        this.sensorRepository = sensorRepository;
        this.leituraRepository = leituraRepository;
        this.leituraService = leituraService;
        this.cadastro = cadastro;
        this.historicoInicialHoras = historicoInicialHoras;
        this.horasMaximasDeRecuperacao = horasMaximasDeRecuperacao;
        this.clock = clock;
    }

    /**
     * Sincroniza a estação e devolve quantas leituras novas foram gravadas.
     *
     * @throws br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException se o provedor falhar
     */
    public int sincronizar(EstacaoMeteorologica estacao) {
        Fazenda fazenda = estacao.getDispositivo().getFazenda();
        if (fazenda.getLatitude() == null || fazenda.getLongitude() == null) {
            throw new RegraDeNegocioException("A fazenda não tem latitude e longitude cadastradas.");
        }
        if (estacao.getDispositivo().getStatus() != StatusDispositivo.ATIVO) {
            throw new RegraDeNegocioException("A estação está inativa e não sincroniza.");
        }
        LocalDateTime agora = LocalDateTime.now(clock).withNano(0);
        LocalDateTime horaAtual = agora.truncatedTo(ChronoUnit.HOURS);

        List<Sensor> sensores = sensorRepository.listarDaEstacao(estacao.getId()).stream()
                .filter(sensor -> sensor.getDispositivo().getStatus() == StatusDispositivo.ATIVO)
                .toList();
        Map<Long, LocalDateTime> ultimaPorSensor = new HashMap<>();
        for (Sensor sensor : sensores) {
            leituraRepository.findFirstByDispositivo_IdOrderByDataHoraDesc(sensor.getId())
                    .ifPresent(leitura -> ultimaPorSensor.put(sensor.getId(), leitura.getDataHora()));
        }

        int horas = horasNecessarias(sensores, ultimaPorSensor, horaAtual);
        List<MedicaoHoraria> medicoes = clima.buscarMedicoesHorarias(
                fazenda.getLatitude(), fazenda.getLongitude(), horas);

        int gravadas = 0;
        int falhas = 0;
        for (Sensor sensor : sensores) {
            LocalDateTime ultima = ultimaPorSensor.get(sensor.getId());
            List<LeituraService.Medicao> novas = medicoes.stream()
                    .filter(m -> !m.horaUtc().isAfter(horaAtual))
                    .filter(m -> ultima == null || m.horaUtc().isAfter(ultima))
                    .map(m -> new LeituraService.Medicao(m.horaUtc(), valor(sensor.getTipoSensor(), m)))
                    .filter(m -> !Double.isNaN(m.valor()))
                    .toList();
            if (novas.isEmpty()) {
                continue;
            }
            try {
                gravadas += leituraService.registrarLote(sensor, novas, OrigemLeitura.EXTERNA);
            } catch (RuntimeException erro) {
                falhas++;
                log.warn("Estação {}: leituras de {} não gravadas: {}",
                        estacao.getId(), sensor.getTipoSensor(), erro.getMessage());
            }
        }
        if (falhas == 0) {
            cadastro.registrarSincronizacao(estacao.getId(), agora);
        }
        return gravadas;
    }

    /** Horas a pedir: o histórico inicial se algum sensor está vazio; senão, desde a leitura mais antiga. */
    int horasNecessarias(List<Sensor> sensores, Map<Long, LocalDateTime> ultimaPorSensor, LocalDateTime horaAtual) {
        boolean algumSemLeitura = sensores.stream().anyMatch(s -> !ultimaPorSensor.containsKey(s.getId()));
        if (sensores.isEmpty() || algumSemLeitura) {
            return historicoInicialHoras;
        }
        LocalDateTime maisAntiga = ultimaPorSensor.values().stream().min(LocalDateTime::compareTo).orElse(horaAtual);
        long lacuna = Duration.between(maisAntiga, horaAtual).toHours() + 1;
        return (int) Math.max(HORAS_MINIMAS, Math.min(lacuna, horasMaximasDeRecuperacao));
    }

    /** Valor da variável na medição, ou NaN se o provedor não o trouxe. */
    private static double valor(VariavelMonitorada variavel, MedicaoHoraria m) {
        Double valor = switch (variavel) {
            case TEMPERATURA_AR -> m.temperaturaCelsius();
            case UMIDADE_AR -> m.umidadeArPercentual();
            case VELOCIDADE_VENTO -> m.ventoKmPorHora();
            case PRECIPITACAO -> m.precipitacaoMm();
            case UMIDADE_SOLO -> m.umidadeSoloPercentual();
            default -> null;
        };
        return valor == null ? Double.NaN : valor;
    }
}
