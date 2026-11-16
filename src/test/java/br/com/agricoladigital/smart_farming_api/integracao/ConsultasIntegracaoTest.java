package br.com.agricoladigital.smart_farming_api.integracao;

import br.com.agricoladigital.smart_farming_api.dto.response.AlertaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.PaginaResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.ContagemAlerta;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import br.com.agricoladigital.smart_farming_api.repository.RecomendacaoIrrigacaoRepository;
import br.com.agricoladigital.smart_farming_api.service.AlertaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Consultas JPQL, especificações e agregações executadas em PostgreSQL real. */
class ConsultasIntegracaoTest extends IntegracaoPostgresBase {
    @Autowired private AlertaService alertaService;
    @Autowired private br.com.agricoladigital.smart_farming_api.service.PlantioService plantioService;
    @Autowired private br.com.agricoladigital.smart_farming_api.service.LeituraService leituraService;
    @Autowired private RecomendacaoIrrigacaoRepository recomendacaoRepository;
    @Autowired private EstacaoMeteorologicaRepository estacaoRepository;

    private Alerta alerta(Talhao talhao, EventoAlerta evento, CriticidadeAlerta criticidade) {
        return alertaRepository.saveAndFlush(new Alerta(regra(evento, criticidade), talhao, null, agoraUtc(), "msg"));
    }

    @Test
    void contagemDeAlertasAbertosPorTalhaoECriticidadeIgnoraFechadosEOutrasFazendas() {
        Usuario dono = novoUsuario(PerfilUsuario.AGRICULTOR);
        Fazenda a = novaFazenda(dono);
        Fazenda b = novaFazenda(dono);
        Talhao a1 = novoTalhao(a, "A1");
        Talhao a2 = novoTalhao(a, "A2");
        Talhao b1 = novoTalhao(b, "B1");
        alerta(a1, EventoAlerta.SECA, CriticidadeAlerta.ALTA);
        alerta(a1, EventoAlerta.RISCO_GEADA, CriticidadeAlerta.BAIXA);
        alerta(a2, EventoAlerta.SECA, CriticidadeAlerta.ALTA);
        alerta(b1, EventoAlerta.SECA, CriticidadeAlerta.MEDIA);
        Alerta fechado = alerta(a2, EventoAlerta.FALHA_SENSOR, CriticidadeAlerta.MEDIA);
        fechado.resolverAutomaticamente(agoraUtc());
        alertaRepository.saveAndFlush(fechado);

        List<ContagemAlerta> contagens = alertaRepository.contarPorTalhaoECriticidade(
                List.of(a.getId()), List.of(StatusAlerta.ABERTO, StatusAlerta.EM_ATENDIMENTO));

        Map<String, Long> porChave = contagens.stream().collect(Collectors.toMap(
                c -> c.talhaoId() + ":" + c.criticidade(), ContagemAlerta::total));
        assertEquals(Map.of(a1.getId() + ":ALTA", 1L, a1.getId() + ":BAIXA", 1L, a2.getId() + ":ALTA", 1L), porChave);
        assertTrue(contagens.stream().allMatch(c -> c.fazendaId().equals(a.getId())));
    }

    @Test
    void listagemDeAlertasFiltraPaginaOrdenaECarregaOsDetalhesSemSessaoAberta() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Fazenda outra = novaFazenda(fazenda.getUsuario());
        Talhao t1 = novoTalhao(fazenda, "T1");
        Talhao t2 = novoTalhao(fazenda, "T2");
        Talhao deOutra = novoTalhao(outra, "X1");
        Sensor sensor = novoSensor(t1, VariavelMonitorada.UMIDADE_SOLO);
        Leitura leitura = leitura(sensor, 12.0, agoraUtc());
        alertaRepository.saveAndFlush(new Alerta(regra(EventoAlerta.SECA, CriticidadeAlerta.ALTA), t1, leitura,
                agoraUtc().minusHours(3), "seca t1"));
        alerta(t2, EventoAlerta.RISCO_GEADA, CriticidadeAlerta.BAIXA);
        alerta(deOutra, EventoAlerta.SECA, CriticidadeAlerta.ALTA);

        PaginaResponse<AlertaResponse> todos = alertaService.listar(fazenda.getId(), null, null, null, null, null, null, 0, 1);
        assertEquals(2, todos.totalElementos());
        assertEquals(2, todos.totalPaginas());
        assertEquals("T2", todos.conteudo().get(0).talhaoIdentificacao(), "mais recente primeiro");

        PaginaResponse<AlertaResponse> secas = alertaService.listar(fazenda.getId(), List.of(StatusAlerta.ABERTO),
                EventoAlerta.SECA, CriticidadeAlerta.ALTA, t1.getId(), null, null, null, null);
        assertEquals(1, secas.totalElementos());
        AlertaResponse resposta = secas.conteudo().get(0);
        assertEquals(12.0, resposta.leitura().valor());
        assertEquals(sensor.getDispositivo().getCodigoIdentificacao(), resposta.leitura().dispositivo());
        assertEquals(CriticidadeAlerta.ALTA, resposta.criticidade());
        assertEquals(20.0, resposta.regra().limiar());

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        assertEquals(1, alertaService.listar(fazenda.getId(), null, null, null, null,
                agora.minusHours(1), agora.plusHours(1), null, null).totalElementos());
        assertEquals(0, alertaService.listar(fazenda.getId(), List.of(StatusAlerta.RESOLVIDO), null, null, null,
                null, null, null, null).totalElementos());
        assertEquals("seca t1", alertaService.buscar(fazenda.getId(), resposta.id()).mensagem());
    }

    // ---- leituras ----

    @Test
    void agregacoesDeLeiturasConsideramSoODispositivoEAJanelaPedidos() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Sensor sensor = novoSensor(talhao, VariavelMonitorada.PRECIPITACAO);
        Sensor outro = novoSensor(talhao, VariavelMonitorada.PRECIPITACAO);
        LocalDateTime agora = agoraUtc();
        leitura(sensor, 10.0, agora.minusHours(2));
        leitura(sensor, 20.0, agora.minusMinutes(30));
        leitura(sensor, 99.0, agora.minusHours(30));      // fora da janela
        leitura(outro, 500.0 / 10, agora.minusHours(1));  // outro dispositivo

        List<Long> ids = List.of(sensor.getId());
        assertEquals(15.0, leituraRepository.mediaNoIntervalo(ids, agora.minusHours(3), agora));
        assertEquals(30.0, leituraRepository.somaNoIntervalo(ids, agora.minusHours(3), agora));
        assertNull(leituraRepository.mediaNoIntervalo(ids, agora.minusMinutes(5), agora));
        assertNull(leituraRepository.somaNoIntervalo(List.of(sensor.getId() + 1000), agora.minusDays(9), agora));
        assertEquals(20.0, leituraRepository.findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(
                ids, agora.minusHours(3), agora).orElseThrow().getValor());
        Collection<Leitura> ultimas = leituraRepository.ultimasPorDispositivos(List.of(sensor.getId(), outro.getId()));
        assertEquals(2, ultimas.size());
        assertEquals(20.0, ultimas.stream().filter(l -> l.getDispositivo().getId().equals(sensor.getId()))
                .findFirst().orElseThrow().getValor());
    }

    @Test
    void sobrescreverValoresAfetaSoLeiturasSimuladasDaJanela() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Sensor sensor = novoSensor(talhao, VariavelMonitorada.UMIDADE_SOLO);
        LocalDateTime agora = agoraUtc();
        leitura(sensor, 45.0, agora.minusHours(1));
        leitura(sensor, 45.0, agora.minusHours(10));    // antes da janela
        leituraRepository.save(new Leitura(sensor.getDispositivo(), 33.0, agora.minusMinutes(10), OrigemLeitura.EXTERNA));

        int alteradas = leituraRepository.sobrescreverValores(List.of(sensor.getId()), OrigemLeitura.SIMULADA,
                agora.minusHours(6), 15.0);

        assertEquals(1, alteradas);
        assertEquals(List.of(15.0, 33.0, 45.0), leituraRepository.findAll().stream()
                .map(Leitura::getValor).sorted().toList());
    }

    // ---- plantios, sensores e recomendações ----

    @Test
    void plantiosEmCultivoDaFazendaVemComCulturaETalhaoCarregados() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Talhao t1 = novoTalhao(fazenda, "T1");
        Talhao t2 = novoTalhao(fazenda, "T2");
        Cultura soja = novaCultura("Soja", 30.0, 38.0, 30);
        plantioEmCultivo(t1, soja);
        plantioRepository.save(new Plantio(t2, soja, "Direto", "2026/2027", java.time.LocalDate.now(), null)); // PLANEJADO

        List<Plantio> emCultivo = plantioRepository.buscarPorFazendaEStatus(fazenda.getId(), StatusPlantio.EM_CULTIVO);

        assertEquals(1, emCultivo.size());
        assertEquals("T1", emCultivo.get(0).getTalhao().getIdentificacao());
        assertEquals(soja.getNome(), emCultivo.get(0).getCultura().getNome());
        assertTrue(emCultivo.get(0).getCultura().temParametrosDeIrrigacao());
    }

    @Test
    void sensoresDeTalhaoNaoMisturamComOsDaEstacao() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Talhao talhao = novoTalhao(fazenda, "T1");
        Sensor deTalhao = novoSensor(talhao, VariavelMonitorada.UMIDADE_SOLO);
        Dispositivo dEstacao = dispositivoRepository.save(
                new Dispositivo("EST", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda));
        jdbc.update("insert into estacao_meteorologica (id_dispositivo, fonte) values (?, 'OPEN_METEO')", dEstacao.getId());
        EstacaoMeteorologica estacao = estacaoRepository.findById(dEstacao.getId()).orElseThrow();
        Dispositivo dSensorEstacao = dispositivoRepository.save(
                new Dispositivo("EST-T", "SENSOR", null, StatusDispositivo.ATIVO, fazenda));
        sensorRepository.save(new Sensor(dSensorEstacao, VariavelMonitorada.TEMPERATURA_AR, estacao));

        assertEquals(List.of(deTalhao.getId()), sensorRepository.listarPorFazenda(fazenda.getId()).stream().map(Sensor::getId).toList());
        assertEquals(List.of(deTalhao.getId()), sensorRepository.listarPorStatus(StatusDispositivo.ATIVO).stream().map(Sensor::getId).toList());
        assertEquals(1, sensorRepository.listarDaEstacao(estacao.getId()).size());
        assertEquals(estacao.getId(), estacaoRepository.buscarDaFazenda(fazenda.getId()).orElseThrow().getId());
    }

    @Test
    void ultimaRecomendacaoDeCadaTalhaoEAdeMaiorId() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Talhao t1 = novoTalhao(fazenda, "T1");
        Talhao t2 = novoTalhao(fazenda, "T2");
        String sql = "insert into recomendacao_irrigacao (id_talhao, data_hora, valido_ate, situacao) "
                + "values (?, '2026-11-16 12:00:00', '2026-11-16 18:00:00', ?)";   // mesmo segundo: desempata por id
        jdbc.update(sql, t1.getId(), "NAO_IRRIGAR");
        jdbc.update(sql, t1.getId(), "SEM_DADOS_SUFICIENTES");
        jdbc.update(sql, t2.getId(), "NAO_IRRIGAR");

        List<RecomendacaoIrrigacao> ultimas = recomendacaoRepository.ultimasDasFazendas(List.of(fazenda.getId()));

        assertEquals(2, ultimas.size());
        Map<Long, SituacaoIrrigacao> porTalhao = ultimas.stream()
                .collect(Collectors.toMap(r -> r.getTalhao().getId(), RecomendacaoIrrigacao::getSituacao));
        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, porTalhao.get(t1.getId()));
        assertEquals(SituacaoIrrigacao.NAO_IRRIGAR, porTalhao.get(t2.getId()));
    }

    @Test
    void listagemGlobalDeAlertasRespeitaOUsuarioEOsFiltros() {
        Usuario ana = novoUsuario(PerfilUsuario.AGRICULTOR);
        Usuario bruno = novoUsuario(PerfilUsuario.AGRICULTOR);
        Fazenda f1 = novaFazenda(ana);
        Fazenda f2 = novaFazenda(ana);
        Fazenda f3 = novaFazenda(bruno);
        alerta(novoTalhao(f1, "T1"), EventoAlerta.SECA, CriticidadeAlerta.ALTA);
        alerta(novoTalhao(f2, "T2"), EventoAlerta.RISCO_GEADA, CriticidadeAlerta.BAIXA);
        alerta(novoTalhao(f3, "T3"), EventoAlerta.SECA, CriticidadeAlerta.MEDIA);

        PaginaResponse<AlertaResponse> deAna = alertaService.listarDoUsuario(ana.getId(), false, null, null, null,
                null, null, null, null, null, null);
        assertEquals(2, deAna.totalElementos());
        assertEquals(java.util.Set.of(f1.getNome(), f2.getNome()), deAna.conteudo().stream()
                .map(AlertaResponse::fazendaNome).collect(Collectors.toSet()));

        assertEquals(1, alertaService.listarDoUsuario(ana.getId(), false, f2.getId(), null, null, null, null, null,
                null, null, null).totalElementos());
        assertEquals(1, alertaService.listarDoUsuario(ana.getId(), false, null, null, EventoAlerta.SECA, null, null,
                null, null, null, null).totalElementos());
        assertEquals(3, alertaService.listarDoUsuario(0L, true, null, List.of(StatusAlerta.ABERTO), null, null, null,
                null, null, null, null).totalElementos(), "o administrador vê todas as fazendas");
        // fazenda de outro usuário: como se não existisse
        assertThrows(br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException.class,
                () -> alertaService.listarDoUsuario(ana.getId(), false, f3.getId(), null, null, null, null, null,
                        null, null, null));
    }

    private LocalDateTime utc(String iso) {
        return LocalDateTime.parse(iso);
    }

    private OffsetDateTime odt(String iso) {
        return LocalDateTime.parse(iso).atOffset(ZoneOffset.UTC);
    }

    @Test
    void agregacaoPorHoraTrazMinimoMediaMaximoEQuantidade() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Sensor sensor = novoSensor(talhao, VariavelMonitorada.TEMPERATURA_AR);
        Sensor outro = novoSensor(talhao, VariavelMonitorada.TEMPERATURA_AR);
        leitura(sensor, 20.0, utc("2026-11-10T10:15:00"));
        leitura(sensor, 24.0, utc("2026-11-10T10:45:00"));
        leitura(sensor, 30.0, utc("2026-11-10T12:05:00"));
        leitura(outro, 99.0, utc("2026-11-10T10:30:00"));        // outro dispositivo

        var resposta = leituraService.agregadasDoSensor(sensor, odt("2026-11-10T00:00:00"), odt("2026-11-10T23:59:59"),
                br.com.agricoladigital.smart_farming_api.entity.IntervaloAgregacao.HORA, null);

        assertEquals("UTC", resposta.fuso());
        assertEquals(2, resposta.pontos().size(), "a hora das 11:00 não tem leituras e não aparece");
        var primeira = resposta.pontos().get(0);
        assertEquals(odt("2026-11-10T10:00:00"), primeira.inicio());
        assertEquals(20.0, primeira.minimo());
        assertEquals(22.0, primeira.media());
        assertEquals(24.0, primeira.maximo());
        assertEquals(2, primeira.quantidade());
        assertEquals(odt("2026-11-10T12:00:00"), resposta.pontos().get(1).inicio());
    }

    @Test
    void agregacaoPorDiaSegueOFusoPedido() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Sensor sensor = novoSensor(talhao, VariavelMonitorada.UMIDADE_SOLO);
        leitura(sensor, 40.0, utc("2026-11-10T01:00:00"));       // 22:00 do dia 9 em São Paulo (UTC-3)
        leitura(sensor, 50.0, utc("2026-11-10T05:00:00"));       // 02:00 do dia 10 em São Paulo
        leitura(sensor, 60.0, utc("2026-11-10T20:00:00"));       // 17:00 do dia 10 em São Paulo

        var emUtc = leituraService.agregadasDoSensor(sensor, odt("2026-11-09T00:00:00"), odt("2026-11-11T00:00:00"),
                br.com.agricoladigital.smart_farming_api.entity.IntervaloAgregacao.DIA, "UTC");
        assertEquals(1, emUtc.pontos().size());
        assertEquals(3, emUtc.pontos().get(0).quantidade());

        var emSaoPaulo = leituraService.agregadasDoSensor(sensor, odt("2026-11-09T00:00:00"), odt("2026-11-11T00:00:00"),
                br.com.agricoladigital.smart_farming_api.entity.IntervaloAgregacao.DIA, "America/Sao_Paulo");
        assertEquals(2, emSaoPaulo.pontos().size());
        assertEquals(odt("2026-11-09T03:00:00"), emSaoPaulo.pontos().get(0).inicio(), "dia 9 local começa às 03:00 UTC");
        assertEquals(1, emSaoPaulo.pontos().get(0).quantidade());
        assertEquals(40.0, emSaoPaulo.pontos().get(0).media());
        assertEquals(odt("2026-11-10T03:00:00"), emSaoPaulo.pontos().get(1).inicio());
        assertEquals(2, emSaoPaulo.pontos().get(1).quantidade());
        assertEquals(55.0, emSaoPaulo.pontos().get(1).media());
    }

    @Test
    void agregacaoSemLeiturasDevolveListaVazia() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Sensor sensor = novoSensor(talhao, VariavelMonitorada.UMIDADE_AR);
        assertTrue(leituraService.agregadasDoSensor(sensor, null, null, null, null).pontos().isEmpty());
    }

    @Test
    void plantiosDaFazendaTrazOsDeTodosOsTalhoesComFiltros() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Fazenda outra = novaFazenda(fazenda.getUsuario());
        Talhao t1 = novoTalhao(fazenda, "T1");
        Talhao t2 = novoTalhao(fazenda, "T2");
        Talhao deOutra = novoTalhao(outra, "X");
        Cultura soja = novaCultura("Soja", 30.0, 38.0, 30);
        Cultura milho = novaCultura("Milho", 32.0, 40.0, 50);
        plantioEmCultivo(t1, soja);
        plantioRepository.save(new Plantio(t2, milho, "Direto", "2025/2026", java.time.LocalDate.now().minusYears(1), null));
        plantioEmCultivo(deOutra, soja);

        assertEquals(2, plantioService.listarDaFazenda(fazenda.getId(), null, null, null, null).size());
        assertEquals(1, plantioService.listarDaFazenda(fazenda.getId(), null, soja.getId(), null, null).size());
        assertEquals(1, plantioService.listarDaFazenda(fazenda.getId(), t2.getId(), null, null, "2025/2026").size());
        assertEquals(1, plantioService.listarDaFazenda(fazenda.getId(), null, null, StatusPlantio.EM_CULTIVO, null).size());
        assertEquals(t1.getId(), plantioService.listarDaFazenda(fazenda.getId(), null, null, StatusPlantio.EM_CULTIVO, null).get(0).talhaoId());
    }
}
