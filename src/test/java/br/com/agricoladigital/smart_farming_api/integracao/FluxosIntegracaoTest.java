package br.com.agricoladigital.smart_farming_api.integracao;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusAlertaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.AlertaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.RecomendacaoIrrigacaoResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.PrevisaoTempoRepository;
import br.com.agricoladigital.smart_farming_api.service.*;
import br.com.agricoladigital.smart_farming_api.service.ConciliadorAlertas.Acao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Fluxos de ponta a ponta nos serviços (alertas, atendimento, irrigação, resumo e mapa) sobre PostgreSQL real. */
class FluxosIntegracaoTest extends IntegracaoPostgresBase {
    @Autowired private AvaliadorAlertas avaliador;
    @Autowired private AlertaService alertaService;
    @Autowired private RecomendadorIrrigacao recomendador;
    @Autowired private RecomendacaoIrrigacaoService recomendacaoService;
    @Autowired private ResumoService resumoService;
    @Autowired private MapaService mapaService;
    @Autowired private PrevisaoTempoRepository previsaoRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Fazenda fazenda;
    private Talhao talhao;
    private Sensor solo;

    private void prepararFazendaComSolo(double umidade) {
        Usuario dono = novoUsuario(PerfilUsuario.AGRICULTOR);
        fazenda = novaFazenda(dono);
        talhao = novoTalhao(fazenda, "T1");
        plantioEmCultivo(talhao, novaCultura("Soja", 30.0, 38.0, 30));
        solo = novoSensor(talhao, VariavelMonitorada.UMIDADE_SOLO);
        definirUmidade(umidade);
    }

    /** Substitui as leituras do sensor de solo por leituras recentes com o valor dado. */
    private void definirUmidade(double umidade) {
        jdbc.update("update alerta set id_leitura = null");
        jdbc.update("delete from leitura where id_dispositivo = ?", solo.getId());
        LocalDateTime agora = agoraUtc();
        for (int minutos : new int[]{5, 40, 90}) {
            leitura(solo, umidade, agora.minusMinutes(minutos));
        }
    }

    private List<Alerta> alertasDoTalhao(EventoAlerta evento) {
        return alertaRepository.findAll().stream()
                .filter(a -> a.getEvento() == evento && a.getTalhao().getId().equals(talhao.getId())).toList();
    }

    private AlterarStatusAlertaRequest pedido(StatusAlerta status, String nota) {
        return new AlterarStatusAlertaRequest(status, nota);
    }

    // ---- alertas ----

    @Test
    void cicloCompletoDeUmAlertaDeSeca() {
        prepararFazendaComSolo(18.0);

        Map<Acao, Integer> primeira = avaliador.avaliarFazenda(fazenda.getId());
        assertEquals(1, primeira.get(Acao.CRIADO));
        Alerta alerta = alertasDoTalhao(EventoAlerta.SECA).get(0);
        assertEquals(CriticidadeAlerta.ALTA, alerta.getCriticidade());
        assertEquals(StatusAlerta.ABERTO, alerta.getStatus());

        // reavaliar não duplica
        assertNull(avaliador.avaliarFazenda(fazenda.getId()).get(Acao.CRIADO));
        assertEquals(1, alertasDoTalhao(EventoAlerta.SECA).size());

        // o agricultor assume e a condição normaliza: o alerta segue com ele, marcado como normalizado
        Usuario dono = fazenda.getUsuario();
        AlertaResponse assumido = alertaService.alterarStatus(fazenda.getId(), alerta.getId(), dono.getId(),
                pedido(StatusAlerta.EM_ATENDIMENTO, "irrigando"));
        assertEquals(dono.getId(), assumido.atendente().id());
        definirUmidade(45.0);
        avaliador.avaliarFazenda(fazenda.getId());
        AlertaResponse depois = alertaService.buscar(fazenda.getId(), alerta.getId());
        assertEquals(StatusAlerta.EM_ATENDIMENTO, depois.status());
        assertNotNull(depois.normalizadoEm());

        AlertaResponse resolvido = alertaService.alterarStatus(fazenda.getId(), alerta.getId(), dono.getId(),
                pedido(StatusAlerta.RESOLVIDO, null));
        assertEquals(ResolucaoAlerta.MANUAL, resolvido.resolucao());

        // a seca volta logo depois: a espera de 6 h impede reabrir com a mesma criticidade
        definirUmidade(18.0);
        Map<Acao, Integer> reabertura = avaliador.avaliarFazenda(fazenda.getId());
        assertEquals(1, reabertura.get(Acao.REABERTURA_ADIADA));
        assertEquals(1, alertasDoTalhao(EventoAlerta.SECA).size());
    }

    @Test
    void alertaNaoAssumidoFechaSozinhoQuandoACondicaoNormaliza() {
        prepararFazendaComSolo(28.0);
        avaliador.avaliarFazenda(fazenda.getId());
        assertEquals(CriticidadeAlerta.BAIXA, alertasDoTalhao(EventoAlerta.SECA).get(0).getCriticidade());

        definirUmidade(18.0);
        assertEquals(1, avaliador.avaliarFazenda(fazenda.getId()).get(Acao.ESCALADO));
        assertEquals(CriticidadeAlerta.ALTA, alertasDoTalhao(EventoAlerta.SECA).get(0).getCriticidade());

        definirUmidade(45.0);
        assertEquals(1, avaliador.avaliarFazenda(fazenda.getId()).get(Acao.RESOLVIDO_AUTOMATICAMENTE));
        Alerta fechado = alertasDoTalhao(EventoAlerta.SECA).get(0);
        assertEquals(StatusAlerta.RESOLVIDO, fechado.getStatus());
        assertEquals(ResolucaoAlerta.AUTOMATICA, fechado.getResolucao());
    }

    @Test
    void avaliacoesSimultaneasNuncaCriamAlertasRepetidos() throws Exception {
        prepararFazendaComSolo(18.0);
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            resultados.add(pool.submit(() -> {
                largada.await();
                try {
                    avaliador.avaliarFazenda(fazenda.getId());
                    return true;
                } catch (RuntimeException colisao) {
                    return false;   // o índice único (ou a versão) barrou a duplicata; o ciclo é refeito depois
                }
            }));
        }
        largada.countDown();
        int sucessos = 0;
        for (Future<Boolean> f : resultados) {
            if (f.get(60, TimeUnit.SECONDS)) {
                sucessos++;
            }
        }
        pool.shutdownNow();

        assertTrue(sucessos >= 1, "ao menos uma avaliação conclui");
        assertEquals(1, alertasDoTalhao(EventoAlerta.SECA).size(), "um só alerta de seca, sem duplicata");
        // depois da corrida, uma avaliação isolada continua funcionando
        assertDoesNotThrow(() -> avaliador.avaliarFazenda(fazenda.getId()));
        assertEquals(1, alertasDoTalhao(EventoAlerta.SECA).size());
    }

    @Test
    void alteracaoSimultaneaDoMesmoAlertaFalhaEmVezDeSobrescrever() throws Exception {
        prepararFazendaComSolo(18.0);
        avaliador.avaliarFazenda(fazenda.getId());
        Long id = alertasDoTalhao(EventoAlerta.SECA).get(0).getId();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> tx.executeWithoutResult(status -> {
            Alerta carregado = alertaRepository.findById(id).orElseThrow();
            // outra operação (conexão própria) altera o mesmo alerta e confirma antes
            try (Connection c = DriverManager.getConnection(jdbcUrl(), jdbcUsuario(), jdbcSenha());
                 var st = c.createStatement()) {
                st.executeUpdate("update alerta set versao = versao + 1, mensagem = 'outra' where id_alerta = " + id);
            } catch (java.sql.SQLException erro) {
                throw new IllegalStateException(erro);
            }
            carregado.assumir(fazenda.getUsuario(), agoraUtc(), "x");
            alertaRepository.saveAndFlush(carregado);
        }));
        assertEquals(StatusAlerta.ABERTO, alertaRepository.findById(id).orElseThrow().getStatus());
    }

    // ---- irrigação, resumo e mapa ----

    private void previsao(int diasAPartirDeHoje, double chuva, double tmax) {
        previsaoRepository.save(new PrevisaoTempo(fazenda, LocalDate.now(java.time.ZoneOffset.UTC).plusDays(diasAPartirDeHoje),
                22.0, 15.0, tmax, chuva, 40, "Open-Meteo", agoraUtc().minusHours(1)));
    }

    @Test
    void recomendacaoDeIrrigacaoGravadaConsultadaEIntegradaAoResumoEAoMapa() {
        prepararFazendaComSolo(28.0);
        previsao(0, 3.0, 29.0);
        previsao(1, 2.0, 31.0);

        RecomendadorIrrigacao.ResumoCalculo calculo = recomendador.calcularFazenda(fazenda.getId());
        assertEquals(1, calculo.gravadas());
        // recalcular sem mudança não grava de novo
        assertEquals(0, recomendador.calcularFazenda(fazenda.getId()).gravadas());

        List<RecomendacaoIrrigacaoResponse> atuais = recomendacaoService.atuais(fazenda.getId());
        assertEquals(1, atuais.size());
        RecomendacaoIrrigacaoResponse r = atuais.get(0);
        assertEquals(SituacaoIrrigacao.IRRIGAR, r.situacao());
        assertEquals(26.0, r.volumeRecomendadoMm());                  // 30 mm de déficit − 0,8 × 5 mm de chuva
        assertEquals(OrigemUmidade.SENSOR_TALHAO, r.umidade().origem());
        assertEquals(5.0, r.chuva().previstaMm());
        assertEquals("Open-Meteo", r.previsao().fonte());
        assertFalse(r.vencida());
        assertEquals(1, recomendacaoService.listar(fazenda.getId(), talhao.getId(), SituacaoIrrigacao.IRRIGAR,
                null, null, null, null).totalElementos());
        assertEquals(r.id(), recomendacaoService.buscar(fazenda.getId(), r.id()).id());

        ResumoFazendaResponse resumo = resumoService.resumoDaFazenda(fazenda.getId());
        assertEquals(1, resumo.irrigacao().irrigar());
        MapaFazendaResponse mapa = mapaService.mapaDaFazenda(fazenda.getId());
        assertEquals(SituacaoIrrigacao.IRRIGAR, mapa.talhoes().get(0).recomendacaoIrrigacao().situacao());

        // o solo recupera: a situação muda e uma nova recomendação é gravada
        definirUmidade(36.0);
        assertEquals(1, recomendador.calcularFazenda(fazenda.getId()).gravadas());
        assertEquals(SituacaoIrrigacao.NAO_IRRIGAR, recomendacaoService.atuais(fazenda.getId()).get(0).situacao());
        assertEquals(2, recomendacaoService.listar(fazenda.getId(), null, null, null, null, null, null).totalElementos());
    }

    @Test
    void semParametrosDaCulturaOuSemPrevisaoNaoHaFalsaCerteza() {
        prepararFazendaComSolo(20.0);                       // solo seco, mas sem previsão cadastrada
        recomendador.calcularFazenda(fazenda.getId());

        RecomendacaoIrrigacaoResponse r = recomendacaoService.atuais(fazenda.getId()).get(0);
        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, r.situacao());
        assertNull(r.volumeRecomendadoMm());
        assertTrue(r.dadosFaltantes().get(0).contains("previsão"));
    }

    @Test
    void resumoEMapaFuncionamComAlertasEmFazendaSemDados() {
        prepararFazendaComSolo(18.0);
        avaliador.avaliarFazenda(fazenda.getId());

        assertEquals(1, resumoService.resumoDaFazenda(fazenda.getId()).alertas().alta());
        assertEquals(CriticidadeAlerta.ALTA, mapaService.mapaDaFazenda(fazenda.getId()).talhoes().get(0).alertas().maiorCriticidade());
        assertEquals(1, resumoService.resumoGeral(fazenda.getUsuario().getId()).totais().alertas().abertos());
    }
}
