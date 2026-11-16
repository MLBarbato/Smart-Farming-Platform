package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.CenarioSimulacaoRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.CenarioSimulacaoResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import br.com.agricoladigital.smart_farming_api.service.ConciliadorAlertas.Acao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Troca o cenário do simulador para demonstrações. Existe só com {@code app.simulador.enabled=true}.
 *
 * <p>Ao trocar, o serviço ajusta as leituras SIMULADAS de solo das últimas horas (para a média da
 * janela da regra já refletir o cenário), grava uma leitura de temperatura na estação (geada) e
 * avalia os alertas das fazendas afetadas na hora, sem esperar o job. Leituras reais ou EXTERNAS
 * nunca são alteradas.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.simulador.enabled", havingValue = "true")
public class CenarioSimulacaoService {
    private final EstadoSimulacao estado;
    private final FazendaRepository fazendaRepository;
    private final SensorRepository sensorRepository;
    private final EstacaoMeteorologicaRepository estacaoRepository;
    private final LeituraRepository leituraRepository;
    private final LeituraService leituraService;
    private final AvaliadorAlertas avaliador;
    private final Duration janela;
    private final Clock clock;

    @Autowired
    public CenarioSimulacaoService(
            EstadoSimulacao estado,
            FazendaRepository fazendaRepository,
            SensorRepository sensorRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            LeituraRepository leituraRepository,
            LeituraService leituraService,
            AvaliadorAlertas avaliador,
            @Value("${app.simulador.janela-cenario-horas:6}") long janelaHoras
    ) {
        this(estado, fazendaRepository, sensorRepository, estacaoRepository, leituraRepository, leituraService,
                avaliador, janelaHoras, Clock.systemUTC());
    }

    CenarioSimulacaoService(
            EstadoSimulacao estado,
            FazendaRepository fazendaRepository,
            SensorRepository sensorRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            LeituraRepository leituraRepository,
            LeituraService leituraService,
            AvaliadorAlertas avaliador,
            long janelaHoras,
            Clock clock
    ) {
        if (janelaHoras < 1 || janelaHoras > 168) {
            throw new IllegalArgumentException("app.simulador.janela-cenario-horas deve estar entre 1 e 168.");
        }
        this.estado = estado;
        this.fazendaRepository = fazendaRepository;
        this.sensorRepository = sensorRepository;
        this.estacaoRepository = estacaoRepository;
        this.leituraRepository = leituraRepository;
        this.leituraService = leituraService;
        this.avaliador = avaliador;
        this.janela = Duration.ofHours(janelaHoras);
        this.clock = clock;
    }

    public CenarioSimulacaoResponse.Estado estadoAtual() {
        return new CenarioSimulacaoResponse.Estado(estado.padrao(), estado.excecoes());
    }

    public CenarioSimulacaoResponse aplicar(CenarioSimulacaoRequest request) {
        Long fazendaId = request.fazendaId();
        if (fazendaId != null && !fazendaRepository.existsById(fazendaId)) {
            throw new RecursoNaoEncontradoException("Fazenda não encontrada: " + fazendaId);
        }
        List<Long> fazendas = fazendaId != null ? List.of(fazendaId) : fazendaRepository.listarIds();
        CenarioSimulacao novo = request.cenario();
        Map<Long, CenarioSimulacao> anteriores = new java.util.HashMap<>();
        fazendas.forEach(id -> anteriores.put(id, estado.de(id)));

        estado.definir(fazendaId, novo);

        List<String> avisos = new ArrayList<>();
        int ajustadas = 0;
        LocalDateTime agora = LocalDateTime.now(clock).withNano(0);
        for (Long id : fazendas) {
            CenarioSimulacao anterior = anteriores.get(id);
            ajustadas += ajustarSolo(id, anterior, novo, agora);
            ajustarEstacao(id, anterior, novo, agora, avisos);
        }

        int criados = 0;
        int escalados = 0;
        int resolvidos = 0;
        for (Long id : fazendas) {
            try {
                Map<Acao, Integer> acoes = avaliador.avaliarFazenda(id);
                criados += acoes.getOrDefault(Acao.CRIADO, 0);
                escalados += acoes.getOrDefault(Acao.ESCALADO, 0);
                resolvidos += acoes.getOrDefault(Acao.RESOLVIDO_AUTOMATICAMENTE, 0);
            } catch (RuntimeException erro) {
                log.warn("Cenário: alertas da fazenda {} não avaliados: {}", id, erro.getMessage());
                avisos.add("Alertas da fazenda " + id + " não puderam ser avaliados agora; o job tentará de novo.");
            }
        }
        return new CenarioSimulacaoResponse(novo, fazendas, ajustadas,
                new CenarioSimulacaoResponse.Alertas(criados, escalados, resolvidos), avisos);
    }

    /** Entrando em SECA ou saindo dela, reescreve as leituras simuladas de solo da janela. */
    private int ajustarSolo(Long fazendaId, CenarioSimulacao anterior, CenarioSimulacao novo, LocalDateTime agora) {
        Double valor = null;
        if (novo == CenarioSimulacao.SECA && anterior != CenarioSimulacao.SECA) {
            valor = GeradorLeituraSimulada.UMIDADE_SOLO_EM_SECA;
        } else if (anterior == CenarioSimulacao.SECA && novo != CenarioSimulacao.SECA) {
            valor = GeradorLeituraSimulada.UMIDADE_SOLO_NORMAL;
        }
        if (valor == null) {
            return 0;
        }
        List<Long> ids = sensorRepository.listarPorFazenda(fazendaId).stream()
                .filter(s -> s.getTipoSensor() == VariavelMonitorada.UMIDADE_SOLO
                        && s.getDispositivo().getStatus() == StatusDispositivo.ATIVO)
                .map(Sensor::getId).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return leituraRepository.sobrescreverValores(ids, OrigemLeitura.SIMULADA, agora.minus(janela), valor);
    }

    /** Entrando em GEADA ou saindo dela, grava uma leitura simulada de temperatura na estação. */
    private void ajustarEstacao(
            Long fazendaId, CenarioSimulacao anterior, CenarioSimulacao novo, LocalDateTime agora, List<String> avisos
    ) {
        Double valor = null;
        if (novo == CenarioSimulacao.GEADA && anterior != CenarioSimulacao.GEADA) {
            valor = GeradorLeituraSimulada.TEMPERATURA_EM_GEADA;
        } else if (anterior == CenarioSimulacao.GEADA && novo != CenarioSimulacao.GEADA) {
            valor = GeradorLeituraSimulada.TEMPERATURA_NORMAL;
        }
        if (valor == null) {
            return;
        }
        Sensor temperatura = estacaoRepository.buscarDaFazenda(fazendaId)
                .filter(e -> e.getDispositivo().getStatus() == StatusDispositivo.ATIVO)
                .flatMap(e -> sensorRepository.listarDaEstacao(e.getId()).stream()
                        .filter(s -> s.getTipoSensor() == VariavelMonitorada.TEMPERATURA_AR
                                && s.getDispositivo().getStatus() == StatusDispositivo.ATIVO)
                        .findFirst())
                .orElse(null);
        if (temperatura == null) {
            if (novo == CenarioSimulacao.GEADA) {
                avisos.add("A fazenda " + fazendaId
                        + " não tem estação ativa com sensor de temperatura; a geada não foi simulada.");
            }
            return;
        }
        try {
            leituraService.registrar(temperatura, valor, agora, OrigemLeitura.SIMULADA);
        } catch (ConflitoException jaExiste) {
            avisos.add("A estação da fazenda " + fazendaId + " já tinha uma leitura neste segundo; repita a troca.");
        }
    }
}
