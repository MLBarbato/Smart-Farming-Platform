package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.LeituraAgregadaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.entity.IntervaloAgregacao;
import br.com.agricoladigital.smart_farming_api.dto.response.SensorLeituraAtualResponse;
import br.com.agricoladigital.smart_farming_api.entity.Leitura;
import br.com.agricoladigital.smart_farming_api.entity.OrigemLeitura;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ingestão e consulta de leituras. Todos os instantes são UTC: a coluna guarda o horário UTC
 * sem fuso e a API devolve {@link OffsetDateTime} com offset {@code Z}.
 */
@Service
@Transactional(readOnly = true)
public class LeituraService {
    public static final int LIMITE_PADRAO = 500;
    public static final int LIMITE_MAXIMO = 5000;
    /** Máximo de intervalos de uma consulta agregada. */
    public static final int MAX_PONTOS = 1000;
    static final Duration JANELA_PADRAO = Duration.ofHours(24);
    /** Tolerância para relógios de sensores levemente adiantados. */
    static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(5);

    private final LeituraRepository leituraRepository;
    private final SensorRepository sensorRepository;
    private final SensorService sensorService;
    private final Clock clock;

    @Autowired
    public LeituraService(
            LeituraRepository leituraRepository,
            SensorRepository sensorRepository,
            SensorService sensorService
    ) {
        this(leituraRepository, sensorRepository, sensorService, Clock.systemUTC());
    }

    LeituraService(
            LeituraRepository leituraRepository,
            SensorRepository sensorRepository,
            SensorService sensorService,
            Clock clock
    ) {
        this.leituraRepository = leituraRepository;
        this.sensorRepository = sensorRepository;
        this.sensorService = sensorService;
        this.clock = clock;
    }

    /** Medição já com o instante em UTC, usada na gravação em lote. */
    public record Medicao(LocalDateTime dataHoraUtc, double valor) {
    }

    /**
     * Valida e grava uma leitura: o sensor precisa estar ATIVO, o valor dentro da faixa da
     * variável, o instante não pode estar no futuro e não pode repetir um instante já gravado.
     */
    @Transactional
    public LeituraResponse registrar(Sensor sensor, Double valor, LocalDateTime dataHoraUtc, OrigemLeitura origem) {
        validar(sensor, valor, dataHoraUtc);
        if (leituraRepository.existsByDispositivo_IdAndDataHora(sensor.getId(), dataHoraUtc)) {
            throw new ConflitoException("Já existe uma leitura deste sensor neste instante.");
        }
        Leitura leitura = leituraRepository.save(
                new Leitura(sensor.getDispositivo(), valor, dataHoraUtc, origem)
        );
        return paraResponse(sensor, leitura);
    }

    /**
     * Grava várias leituras de uma vez, numa única transação: se qualquer uma for inválida ou
     * repetir um instante já gravado, nenhuma é gravada. Retorna quantas foram gravadas.
     */
    @Transactional
    public int registrarLote(Sensor sensor, List<Medicao> medicoes, OrigemLeitura origem) {
        medicoes.forEach(medicao -> validar(sensor, medicao.valor(), medicao.dataHoraUtc()));
        List<Leitura> leituras = medicoes.stream()
                .map(medicao -> new Leitura(sensor.getDispositivo(), medicao.valor(), medicao.dataHoraUtc(), origem))
                .toList();
        return leituraRepository.saveAll(leituras).size();
    }

    public boolean temLeituras(Long sensorId) {
        return leituraRepository.existsByDispositivo_Id(sensorId);
    }

    private void validar(Sensor sensor, Double valor, LocalDateTime dataHoraUtc) {
        if (sensor.getDispositivo().getStatus() != StatusDispositivo.ATIVO) {
            throw new RegraDeNegocioException("O sensor não está ativo e não aceita leituras.");
        }
        VariavelMonitorada variavel = sensor.getTipoSensor();
        if (valor == null || !variavel.aceita(valor)) {
            throw new RegraDeNegocioException(
                    "Valor fora da faixa válida de " + variavel + ": de " + variavel.getMinimo()
                            + " a " + variavel.getMaximo() + " " + variavel.getUnidade() + "."
            );
        }
        if (dataHoraUtc.isAfter(agoraUtc().plus(TOLERANCIA_FUTURO))) {
            throw new RegraDeNegocioException("A data/hora da leitura está no futuro.");
        }
    }

    /** Leituras do sensor no intervalo (padrão: últimas 24 h), em ordem cronológica. */
    public List<LeituraResponse> historico(
            Long fazendaId, Long talhaoId, Long sensorId, OffsetDateTime de, OffsetDateTime ate, Integer limite
    ) {
        return historicoDoSensor(sensorService.localizar(fazendaId, talhaoId, sensorId), de, ate, limite);
    }

    /** Mesmo histórico, para um sensor já localizado (sensores de talhão ou de estação). */
    public List<LeituraResponse> historicoDoSensor(Sensor sensor, OffsetDateTime de, OffsetDateTime ate, Integer limite) {
        Long sensorId = sensor.getId();
        LocalDateTime fim = ate != null ? paraUtc(ate) : agoraUtc();
        LocalDateTime inicio = de != null ? paraUtc(de) : fim.minus(JANELA_PADRAO);
        if (inicio.isAfter(fim)) {
            throw new RegraDeNegocioException("'de' deve ser anterior ou igual a 'ate'.");
        }
        int tamanho = limite == null ? LIMITE_PADRAO : limite;
        if (tamanho < 1 || tamanho > LIMITE_MAXIMO) {
            throw new RegraDeNegocioException("'limite' deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        List<Leitura> encontradas = new ArrayList<>(
                leituraRepository.findByDispositivo_IdAndDataHoraBetweenOrderByDataHoraDesc(
                        sensorId, inicio, fim, PageRequest.of(0, tamanho)
                )
        );
        Collections.reverse(encontradas);
        return encontradas.stream().map(leitura -> paraResponse(sensor, leitura)).toList();
    }

    /** Leituras agregadas de um sensor do talhão (ver {@link #agregadasDoSensor}). */
    public LeituraAgregadaResponse agregadas(
            Long fazendaId, Long talhaoId, Long sensorId, OffsetDateTime de, OffsetDateTime ate,
            IntervaloAgregacao intervalo, String fuso
    ) {
        return agregadasDoSensor(sensorService.localizar(fazendaId, talhaoId, sensorId), de, ate, intervalo, fuso);
    }

    /**
     * Mínimo, média, máximo e quantidade das leituras por hora ou por dia (padrão: por hora, últimos 7 dias; por dia,
     * últimos 30). Os intervalos seguem o fuso informado (padrão UTC) e, ao todo, são no máximo {@value #MAX_PONTOS}.
     */
    public LeituraAgregadaResponse agregadasDoSensor(
            Sensor sensor, OffsetDateTime de, OffsetDateTime ate, IntervaloAgregacao intervalo, String fuso
    ) {
        IntervaloAgregacao unidade = intervalo == null ? IntervaloAgregacao.HORA : intervalo;
        String zona = fuso == null || fuso.isBlank() ? "UTC" : fuso.trim();
        try {
            ZoneId.of(zona);
        } catch (DateTimeException erro) {
            throw new RegraDeNegocioException("Fuso inválido: use um nome IANA, como America/Sao_Paulo.");
        }
        LocalDateTime fim = ate != null ? paraUtc(ate) : agoraUtc();
        LocalDateTime inicio = de != null ? paraUtc(de) : fim.minus(unidade.getPeriodoPadrao());
        if (inicio.isAfter(fim)) {
            throw new RegraDeNegocioException("'de' deve ser anterior ou igual a 'ate'.");
        }
        if (Duration.between(inicio, fim).dividedBy(unidade.getDuracao()) > MAX_PONTOS) {
            throw new RegraDeNegocioException("O período tem mais de " + MAX_PONTOS + " intervalos de "
                    + unidade.name().toLowerCase() + "; reduza o período ou use o intervalo DIA.");
        }
        List<LeituraAgregadaResponse.Ponto> pontos = leituraRepository
                .agregar(sensor.getId(), unidade.getUnidadeSql(), zona, inicio, fim).stream()
                .map(linha -> new LeituraAgregadaResponse.Ponto(
                        Instant.ofEpochSecond(((Number) linha[0]).longValue()).atOffset(ZoneOffset.UTC),
                        ((Number) linha[1]).doubleValue(),
                        arredondar(((Number) linha[2]).doubleValue()),
                        ((Number) linha[3]).doubleValue(),
                        ((Number) linha[4]).longValue()))
                .toList();
        return new LeituraAgregadaResponse(sensor.getId(), sensor.getTipoSensor(), sensor.getUnidadeMedida(), unidade,
                zona, inicio.atOffset(ZoneOffset.UTC), fim.atOffset(ZoneOffset.UTC), pontos);
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 1000.0) / 1000.0;
    }

    public LeituraResponse ultima(Long fazendaId, Long talhaoId, Long sensorId) {
        Sensor sensor = sensorService.localizar(fazendaId, talhaoId, sensorId);
        return leituraRepository.findFirstByDispositivo_IdOrderByDataHoraDesc(sensorId)
                .map(leitura -> paraResponse(sensor, leitura))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "O sensor ainda não tem leituras: " + sensorId
                ));
    }

    /** Última leitura de cada sensor do talhão; sensores sem leitura vêm com {@code ultimaLeitura} nula. */
    public List<SensorLeituraAtualResponse> atuais(Long fazendaId, Long talhaoId) {
        sensorService.encontrarTalhao(fazendaId, talhaoId);
        return sensorRepository.listarPorTalhao(talhaoId).stream()
                .map(sensor -> new SensorLeituraAtualResponse(
                        SensorService.paraResponse(sensor),
                        leituraRepository.findFirstByDispositivo_IdOrderByDataHoraDesc(sensor.getId())
                                .map(leitura -> paraResponse(sensor, leitura))
                                .orElse(null)
                ))
                .toList();
    }

    private LocalDateTime agoraUtc() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private static LocalDateTime paraUtc(OffsetDateTime instante) {
        return instante.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static LeituraResponse paraResponse(Sensor sensor, Leitura leitura) {
        return new LeituraResponse(
                sensor.getId(),
                sensor.getTipoSensor(),
                leitura.getValor(),
                sensor.getUnidadeMedida(),
                leitura.getDataHora().atOffset(ZoneOffset.UTC),
                leitura.getOrigem()
        );
    }
}
