package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.EstacaoResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.LeituraAgregadaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.entity.IntervaloAgregacao;
import br.com.agricoladigital.smart_farming_api.entity.EstacaoMeteorologica;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.Leitura;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoEstacao;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API da estação virtual da fazenda. Não abre transação própria: as chamadas ao provedor ficam
 * fora de transações, e cada consulta ao banco traz o que precisa com fetch join.
 */
@Slf4j
@Service
public class EstacaoService {
    private final EstacaoCadastro cadastro;
    private final EstacaoSincronizador sincronizador;
    private final SensorRepository sensorRepository;
    private final LeituraRepository leituraRepository;
    private final LeituraService leituraService;
    private final Duration intervaloMinimoManual;
    private final Clock clock;

    @Autowired
    public EstacaoService(
            EstacaoCadastro cadastro,
            EstacaoSincronizador sincronizador,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            LeituraService leituraService,
            @Value("${app.estacao.intervalo-minimo-manual-minutos:5}") long intervaloMinimoManualMinutos
    ) {
        this(cadastro, sincronizador, sensorRepository, leituraRepository, leituraService,
                intervaloMinimoManualMinutos, Clock.systemUTC());
    }

    EstacaoService(
            EstacaoCadastro cadastro,
            EstacaoSincronizador sincronizador,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            LeituraService leituraService,
            long intervaloMinimoManualMinutos,
            Clock clock
    ) {
        if (intervaloMinimoManualMinutos < 0) {
            throw new IllegalArgumentException("app.estacao.intervalo-minimo-manual-minutos não pode ser negativo.");
        }
        this.cadastro = cadastro;
        this.sincronizador = sincronizador;
        this.sensorRepository = sensorRepository;
        this.leituraRepository = leituraRepository;
        this.leituraService = leituraService;
        this.intervaloMinimoManual = Duration.ofMinutes(intervaloMinimoManualMinutos);
        this.clock = clock;
    }

    /** Cria a estação e tenta já carregar o histórico; se o provedor falhar, o job completa depois. */
    public EstacaoResponse criar(Long fazendaId) {
        cadastro.criar(fazendaId);
        EstacaoMeteorologica estacao = cadastro.encontrar(fazendaId);
        try {
            sincronizador.sincronizar(estacao);
        } catch (PrevisaoIndisponivelException erro) {
            log.warn("Estação da fazenda {} criada, mas a primeira sincronização falhou: {}",
                    fazendaId, erro.getMessage());
        }
        return consultar(fazendaId);
    }

    public EstacaoResponse consultar(Long fazendaId) {
        EstacaoMeteorologica estacao = cadastro.encontrar(fazendaId);
        List<Sensor> sensores = sensorRepository.listarDaEstacao(estacao.getId());
        Map<Long, Leitura> ultimas = sensores.isEmpty() ? Map.of()
                : leituraRepository.ultimasPorDispositivos(sensores.stream().map(Sensor::getId).toList()).stream()
                .collect(Collectors.toMap(l -> l.getDispositivo().getId(), Function.identity()));
        List<LeituraResponse> leituras = sensores.stream()
                .filter(sensor -> ultimas.containsKey(sensor.getId()))
                .map(sensor -> paraResponse(sensor, ultimas.get(sensor.getId())))
                .toList();
        Fazenda fazenda = estacao.getDispositivo().getFazenda();
        return new EstacaoResponse(
                estacao.getId(),
                estacao.getDispositivo().getCodigoIdentificacao(),
                estacao.getModelo(),
                estacao.getFonte(),
                estacao.getDispositivo().getStatus(),
                estacao.getFrequenciaColeta(),
                fazenda.getLatitude(),
                fazenda.getLongitude(),
                estacao.getUltimaSincronizacao() == null ? null
                        : estacao.getUltimaSincronizacao().atOffset(ZoneOffset.UTC),
                SituacaoEstacao.de(estacao, agoraUtc()),
                PrevisaoService.ATRIBUICAO,
                leituras
        );
    }

    /**
     * Força a sincronização. Se a última foi há menos que o intervalo mínimo, devolve o estado
     * atual sem chamar o provedor (protege o limite de uso da API).
     */
    public EstacaoResponse sincronizarManualmente(Long fazendaId) {
        EstacaoMeteorologica estacao = cadastro.encontrar(fazendaId);
        if (estacao.getDispositivo().getStatus() != StatusDispositivo.ATIVO) {
            throw new RegraDeNegocioException("A estação está inativa e não sincroniza.");
        }
        LocalDateTime ultima = estacao.getUltimaSincronizacao();
        if (ultima == null || Duration.between(ultima, agoraUtc()).compareTo(intervaloMinimoManual) >= 0) {
            sincronizador.sincronizar(estacao);
        }
        return consultar(fazendaId);
    }

    public EstacaoResponse alterarStatus(Long fazendaId, StatusDispositivo status) {
        cadastro.alterarStatus(fazendaId, status);
        return consultar(fazendaId);
    }

    public void remover(Long fazendaId) {
        cadastro.remover(fazendaId);
    }

    /** Histórico de uma variável da estação (padrão: últimas 24 h), em ordem cronológica. */
    public List<LeituraResponse> historico(
            Long fazendaId, VariavelMonitorada variavel, OffsetDateTime de, OffsetDateTime ate, Integer limite
    ) {
        EstacaoMeteorologica estacao = cadastro.encontrar(fazendaId);
        Sensor sensor = sensorRepository.listarDaEstacao(estacao.getId()).stream()
                .filter(s -> s.getTipoSensor() == variavel)
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException("A estação não mede a variável " + variavel + "."));
        return leituraService.historicoDoSensor(sensor, de, ate, limite);
    }

    /** Leituras agregadas (hora ou dia) de uma variável da estação. */
    public LeituraAgregadaResponse agregadas(
            Long fazendaId, VariavelMonitorada variavel, OffsetDateTime de, OffsetDateTime ate,
            IntervaloAgregacao intervalo, String fuso
    ) {
        EstacaoMeteorologica estacao = cadastro.encontrar(fazendaId);
        Sensor sensor = sensorRepository.listarDaEstacao(estacao.getId()).stream()
                .filter(s -> s.getTipoSensor() == variavel)
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException("A estação não mede a variável " + variavel + "."));
        return leituraService.agregadasDoSensor(sensor, de, ate, intervalo, fuso);
    }

    private static LeituraResponse paraResponse(Sensor sensor, Leitura leitura) {
        return new LeituraResponse(
                sensor.getId(), sensor.getTipoSensor(), leitura.getValor(), sensor.getUnidadeMedida(),
                leitura.getDataHora().atOffset(ZoneOffset.UTC), leitura.getOrigem()
        );
    }

    private LocalDateTime agoraUtc() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
