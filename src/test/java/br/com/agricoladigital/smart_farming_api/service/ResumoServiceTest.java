package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoAlertasResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse.CondicaoAtual;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoGeralResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.ContagemAlerta;
import br.com.agricoladigital.smart_farming_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumoServiceTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);

    @Mock private FazendaRepository fazendaRepository;
    @Mock private TalhaoRepository talhaoRepository;
    @Mock private PlantioRepository plantioRepository;
    @Mock private SensorRepository sensorRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private EstacaoMeteorologicaRepository estacaoRepository;
    @Mock private AlertaRepository alertaRepository;
    @Mock private RecomendacaoIrrigacaoRepository recomendacaoRepository;

    private ResumoService resumoService;
    private Fazenda fazenda;
    private Talhao talhaoA;
    private Talhao talhaoB;
    private Cultura soja;

    @BeforeEach
    void preparar() {
        resumoService = new ResumoService(fazendaRepository, talhaoRepository, plantioRepository, sensorRepository,
                leituraRepository, estacaoRepository, alertaRepository, recomendacaoRepository, 30, Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        lenient().when(estacaoRepository.listarDasFazendas(any())).thenReturn(List.of());
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("Santa Maria", "Rio Verde", 1240.0, null, null, "Rio Verde", "GO", dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        talhaoA = talhao("A1", 10L, 50.0);
        talhaoB = talhao("B1", 11L, 30.0);
        soja = new Cultura("Soja", 120);
    }

    private Talhao talhao(String nome, long id, double area) {
        Talhao talhao = new Talhao(nome, area, null, fazenda);
        ReflectionTestUtils.setField(talhao, "id", id);
        return talhao;
    }

    private Plantio plantio(Talhao talhao, StatusPlantio status, String safra, Double kg) {
        Plantio plantio = new Plantio(talhao, soja, "Direto", safra, LocalDate.of(2026, 1, 1), null);
        ReflectionTestUtils.setField(plantio, "status", status);
        ReflectionTestUtils.setField(plantio, "quantidadeColhidaKg", kg);
        return plantio;
    }

    private Sensor sensor(long id, Talhao talhao, VariavelMonitorada variavel, StatusDispositivo status) {
        Sensor sensor = new Sensor(new Dispositivo("S-" + id, "SENSOR", null, status, talhao), variavel);
        ReflectionTestUtils.setField(sensor, "id", id);
        ReflectionTestUtils.setField(sensor.getDispositivo(), "id", id);
        return sensor;
    }

    private Leitura leitura(Sensor sensor, double valor, LocalDateTime dataHora) {
        return new Leitura(sensor.getDispositivo(), valor, dataHora, OrigemLeitura.SIMULADA);
    }

    private void dadosDaFazenda(List<Plantio> plantios, List<Sensor> sensores, List<Leitura> leituras) {
        when(talhaoRepository.findAllByFazenda_IdIn(any())).thenReturn(List.of(talhaoA, talhaoB));
        when(plantioRepository.buscarPorFazendas(any())).thenReturn(plantios);
        when(sensorRepository.listarPorFazendas(any())).thenReturn(sensores);
        lenient().when(leituraRepository.ultimasPorDispositivos(any())).thenReturn(leituras);
    }

    @Test
    void resumoContaTalhoesAreasPlantiosECulturas() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        dadosDaFazenda(List.of(
                plantio(talhaoA, StatusPlantio.EM_CULTIVO, "2026/2027", null),
                plantio(talhaoB, StatusPlantio.PLANEJADO, "2026/2027", null),
                plantio(talhaoB, StatusPlantio.CANCELADO, "2025/2026", null)), List.of(), List.of());

        ResumoFazendaResponse resumo = resumoService.resumoDaFazenda(1L);

        assertEquals(2, resumo.talhoes().total());
        assertEquals(80.0, resumo.talhoes().areaHectares());
        assertEquals(1, resumo.talhoes().comPlantioEmCultivo());
        assertEquals(50.0, resumo.talhoes().areaEmCultivoHectares());
        assertEquals(1, resumo.plantios().emCultivo());
        assertEquals(1, resumo.plantios().planejados());
        assertEquals(1, resumo.plantios().cancelados());
        assertEquals(0, resumo.plantios().colhidos());
        assertEquals("Soja", resumo.culturasEmCultivo().get(0).cultura());
        assertEquals(1, resumo.culturasEmCultivo().get(0).talhoes());
        assertEquals(50.0, resumo.culturasEmCultivo().get(0).areaHectares());
        assertEquals(30, resumo.criterioSemLeituraRecenteMinutos());
    }

    @Test
    void sensoresPorEstadoESemLeituraRecenteContamSoAtivos() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        Sensor recente = sensor(100, talhaoA, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        Sensor velho = sensor(101, talhaoA, VariavelMonitorada.UMIDADE_SOLO, StatusDispositivo.ATIVO);
        Sensor nuncaLeu = sensor(102, talhaoB, VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO);
        Sensor inativo = sensor(103, talhaoB, VariavelMonitorada.PH_SOLO, StatusDispositivo.INATIVO);
        Sensor manutencao = sensor(104, talhaoB, VariavelMonitorada.PH_SOLO, StatusDispositivo.MANUTENCAO);
        dadosDaFazenda(List.of(), List.of(recente, velho, nuncaLeu, inativo, manutencao), List.of(
                leitura(recente, 25.0, AGORA.minusMinutes(5)),
                leitura(velho, 40.0, AGORA.minusMinutes(31))));

        ResumoFazendaResponse.Sensores sensores = resumoService.resumoDaFazenda(1L).sensores();

        assertEquals(5, sensores.total());
        assertEquals(3, sensores.ativos());
        assertEquals(1, sensores.inativos());
        assertEquals(1, sensores.emManutencao());
        assertEquals(2, sensores.semLeituraRecente());
    }

    @Test
    void leituraNoLimiteDoCriterioAindaContaComoRecente() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        Sensor sensor = sensor(100, talhaoA, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        dadosDaFazenda(List.of(), List.of(sensor), List.of(leitura(sensor, 25.0, AGORA.minusMinutes(30))));

        assertEquals(0, resumoService.resumoDaFazenda(1L).sensores().semLeituraRecente());
    }

    @Test
    void condicoesAtuaisAgrupamPorVariavelIgnorandoLeiturasVelhasEInativos() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        Sensor t1 = sensor(100, talhaoA, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        Sensor t2 = sensor(101, talhaoB, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        Sensor t3 = sensor(102, talhaoB, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        Sensor t4 = sensor(103, talhaoB, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.INATIVO);
        dadosDaFazenda(List.of(), List.of(t1, t2, t3, t4), List.of(
                leitura(t1, 20.0, AGORA.minusMinutes(10)),
                leitura(t2, 25.0, AGORA.minusMinutes(2)),
                leitura(t3, 99.0, AGORA.minusHours(3)),
                leitura(t4, 99.0, AGORA.minusMinutes(1))));

        List<CondicaoAtual> condicoes = resumoService.resumoDaFazenda(1L).condicoesAtuais();

        assertEquals(1, condicoes.size());
        CondicaoAtual temperatura = condicoes.get(0);
        assertEquals(VariavelMonitorada.TEMPERATURA_AR, temperatura.variavel());
        assertEquals("°C", temperatura.unidade());
        assertEquals(22.5, temperatura.media());
        assertEquals(20.0, temperatura.minimo());
        assertEquals(25.0, temperatura.maximo());
        assertEquals(2, temperatura.sensores());
        assertEquals(AGORA.minusMinutes(2), temperatura.ultimaAtualizacao().toLocalDateTime());
    }

    @Test
    void produtividadePorSafraSomaKgESobreAreaDosTalhoesColhidos() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        dadosDaFazenda(List.of(
                plantio(talhaoA, StatusPlantio.COLHIDO, "2025/2026", 15_000.0),   // 50 ha
                plantio(talhaoB, StatusPlantio.COLHIDO, "2025/2026", 9_000.0),    // 30 ha
                plantio(talhaoA, StatusPlantio.COLHIDO, "2024/2025", 10_000.0),
                plantio(talhaoB, StatusPlantio.EM_CULTIVO, "2026/2027", null)), List.of(), List.of());

        List<ResumoFazendaResponse.ProdutividadeSafra> safras = resumoService.resumoDaFazenda(1L).produtividadePorSafra();

        assertEquals(List.of("2025/2026", "2024/2025"), safras.stream().map(s -> s.safra()).toList());
        assertEquals(2, safras.get(0).plantiosColhidos());
        assertEquals(24_000.0, safras.get(0).quantidadeColhidaKg());
        assertEquals(80.0, safras.get(0).areaHectares());
        assertEquals(300.0, safras.get(0).produtividadeKgPorHectare());
        assertEquals(200.0, safras.get(1).produtividadeKgPorHectare());
    }

    @Test
    void fazendaSemNadaRetornaZeros() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.findAllByFazenda_IdIn(any())).thenReturn(List.of());
        when(plantioRepository.buscarPorFazendas(any())).thenReturn(List.of());
        when(sensorRepository.listarPorFazendas(any())).thenReturn(List.of());

        ResumoFazendaResponse resumo = resumoService.resumoDaFazenda(1L);

        assertEquals(0, resumo.talhoes().total());
        assertEquals(0, resumo.sensores().total());
        assertTrue(resumo.condicoesAtuais().isEmpty());
        assertTrue(resumo.produtividadePorSafra().isEmpty());
        verify(leituraRepository, never()).ultimasPorDispositivos(any());
    }

    @Test
    void fazendaInexistenteRetornaNaoEncontrado() {
        when(fazendaRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> resumoService.resumoDaFazenda(9L));
    }

    @Test
    void resumoGeralDoUsuarioTrazCardsETotais() {
        Fazenda outra = new Fazenda("Boa Esperança", "Sorriso", 860.0, null, null, "Sorriso", "MT", fazenda.getUsuario());
        ReflectionTestUtils.setField(outra, "id", 2L);
        Talhao talhaoC = talhao("C1", 12L, 20.0);
        ReflectionTestUtils.setField(talhaoC, "fazenda", outra);
        when(fazendaRepository.findAllByUsuario_Id(7L)).thenReturn(List.of(fazenda, outra));
        when(talhaoRepository.findAllByFazenda_IdIn(any())).thenReturn(List.of(talhaoA, talhaoB, talhaoC));
        when(plantioRepository.buscarPorFazendas(any())).thenReturn(List.of(
                plantio(talhaoA, StatusPlantio.EM_CULTIVO, "2026/2027", null),
                plantio(talhaoC, StatusPlantio.EM_CULTIVO, "2026/2027", null)));
        Sensor sensor = sensor(100, talhaoA, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(sensorRepository.listarPorFazendas(any())).thenReturn(List.of(sensor));
        when(leituraRepository.ultimasPorDispositivos(any())).thenReturn(List.of(leitura(sensor, 25.0, AGORA)));

        ResumoGeralResponse geral = resumoService.resumoGeral(7L);

        assertEquals(List.of("Boa Esperança", "Santa Maria"), geral.fazendas().stream().map(c -> c.nome()).toList());
        ResumoGeralResponse.FazendaCard santaMaria = geral.fazendas().get(1);
        assertEquals(2, santaMaria.talhoes());
        assertEquals(1, santaMaria.sensoresAtivos());
        assertEquals(List.of("Soja"), santaMaria.culturasEmCultivo());
        assertEquals(2, geral.totais().fazendas());
        assertEquals(3, geral.totais().talhoes());
        assertEquals(2100.0, geral.totais().areaTotalHectares());
        assertEquals(70.0, geral.totais().areaEmCultivoHectares());
        assertEquals(2, geral.totais().plantiosEmCultivo());
        assertEquals(1, geral.totais().sensoresAtivos());
        assertEquals(0, geral.totais().sensoresSemLeituraRecente());
    }

    @Test
    void resumoGeralDoAdministradorUsaTodasAsFazendasESemFazendasNaoConsultaMais() {
        when(fazendaRepository.findAll(any(Sort.class))).thenReturn(List.of());

        ResumoGeralResponse geral = resumoService.resumoGeral(null);

        assertEquals(0, geral.totais().fazendas());
        assertTrue(geral.fazendas().isEmpty());
        verifyNoInteractions(talhaoRepository, plantioRepository, sensorRepository, leituraRepository);
    }

    @Test
    void configuracaoInvalidaEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> new ResumoService(fazendaRepository, talhaoRepository,
                plantioRepository, sensorRepository, leituraRepository, estacaoRepository, alertaRepository, recomendacaoRepository, 0, Clock.systemUTC()));
    }

    @Test
    void resumoTrazAEstacaoComSituacaoELeiturasAtuais() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        dadosDaFazenda(List.of(), List.of(), List.of());
        Dispositivo dEstacao = new Dispositivo("EST-1", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        EstacaoMeteorologica estacao = new EstacaoMeteorologica(dEstacao, "m", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 50L);
        estacao.registrarSincronizacao(AGORA.minusMinutes(20));
        Sensor temperatura = new Sensor(new Dispositivo("EST-1-T", "SENSOR", null, StatusDispositivo.ATIVO, fazenda),
                VariavelMonitorada.TEMPERATURA_AR, estacao);
        ReflectionTestUtils.setField(temperatura, "id", 51L);
        ReflectionTestUtils.setField(temperatura.getDispositivo(), "id", 51L);
        when(estacaoRepository.listarDasFazendas(any())).thenReturn(List.of(estacao));
        when(sensorRepository.listarDasEstacoes(any())).thenReturn(List.of(temperatura));
        when(leituraRepository.ultimasPorDispositivos(any())).thenReturn(List.of(
                new Leitura(temperatura.getDispositivo(), 26.4, AGORA.minusHours(1), OrigemLeitura.EXTERNA)));

        ResumoFazendaResponse resumo = resumoService.resumoDaFazenda(1L);

        assertEquals(SituacaoEstacao.ATUAL, resumo.estacao().situacao());
        assertEquals(1, resumo.estacao().leiturasAtuais().size());
        assertEquals(26.4, resumo.estacao().leiturasAtuais().get(0).valor());
        assertEquals(OrigemLeitura.EXTERNA, resumo.estacao().leiturasAtuais().get(0).origem());
        assertEquals(0, resumo.sensores().total());   // sensores da estação não entram na contagem de talhões
    }

    @Test
    void fazendaSemEstacaoTemEstacaoNula() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        dadosDaFazenda(List.of(), List.of(), List.of());

        assertNull(resumoService.resumoDaFazenda(1L).estacao());
    }

    @Test
    void resumoDaFazendaContaAlertasAbertosPorCriticidade() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        dadosDaFazenda(List.of(), List.of(), List.of());
        when(alertaRepository.contarPorTalhaoECriticidade(any(), any())).thenReturn(List.of(
                new ContagemAlerta(1L, 10L, CriticidadeAlerta.ALTA, 1),
                new ContagemAlerta(1L, 10L, CriticidadeAlerta.BAIXA, 2),
                new ContagemAlerta(1L, 11L, CriticidadeAlerta.BAIXA, 1)));

        ResumoAlertasResponse alertas = resumoService.resumoDaFazenda(1L).alertas();

        assertEquals(4, alertas.abertos());
        assertEquals(3, alertas.baixa());
        assertEquals(0, alertas.media());
        assertEquals(1, alertas.alta());
        assertEquals(CriticidadeAlerta.ALTA, alertas.maiorCriticidade());
    }

    @Test
    void semAlertasAbertosAMaiorCriticidadeENula() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        dadosDaFazenda(List.of(), List.of(), List.of());

        ResumoAlertasResponse alertas = resumoService.resumoDaFazenda(1L).alertas();

        assertEquals(0, alertas.abertos());
        assertNull(alertas.maiorCriticidade());
    }

    @Test
    void resumoGeralSomaOsAlertasDeCadaFazendaEOsMostraNosCards() {
        Fazenda outra = new Fazenda("Boa Esperança", "Sorriso", 860.0, null, null, "Sorriso", "MT", fazenda.getUsuario());
        ReflectionTestUtils.setField(outra, "id", 2L);
        when(fazendaRepository.findAllByUsuario_Id(7L)).thenReturn(List.of(fazenda, outra));
        when(talhaoRepository.findAllByFazenda_IdIn(any())).thenReturn(List.of(talhaoA));
        when(plantioRepository.buscarPorFazendas(any())).thenReturn(List.of());
        when(sensorRepository.listarPorFazendas(any())).thenReturn(List.of());
        when(alertaRepository.contarPorTalhaoECriticidade(any(), any())).thenReturn(List.of(
                new ContagemAlerta(1L, 10L, CriticidadeAlerta.MEDIA, 2),
                new ContagemAlerta(2L, 20L, CriticidadeAlerta.ALTA, 1)));

        ResumoGeralResponse geral = resumoService.resumoGeral(7L);

        ResumoGeralResponse.FazendaCard santaMaria = geral.fazendas().stream()
                .filter(c -> c.id() == 1L).findFirst().orElseThrow();
        assertEquals(2, santaMaria.alertas().abertos());
        assertEquals(CriticidadeAlerta.MEDIA, santaMaria.alertas().maiorCriticidade());
        assertEquals(3, geral.totais().alertas().abertos());
        assertEquals(1, geral.totais().alertas().alta());
        assertEquals(2, geral.totais().alertas().media());
        assertEquals(CriticidadeAlerta.ALTA, geral.totais().alertas().maiorCriticidade());
    }

    private RecomendacaoIrrigacao recomendacao(Talhao talhao, SituacaoIrrigacao situacao) {
        InsumosIrrigacao insumos = new InsumosIrrigacao("Soja", 30.0, 38.0, 30, 28.0, OrigemUmidade.SENSOR_TALHAO,
                null, null, null, null, false, List.of());
        RecomendacaoIrrigacao r = new RecomendacaoIrrigacao(talhao, null, AGORA, AGORA.plusHours(6), situacao,
                situacao == SituacaoIrrigacao.IRRIGAR ? 20.0 : null, "x", insumos);
        ReflectionTestUtils.setField(r, "id", 1L);
        return r;
    }

    @Test
    void resumoContaAsRecomendacoesDeIrrigacaoSoDosTalhoesEmCultivo() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        dadosDaFazenda(List.of(plantio(talhaoA, StatusPlantio.EM_CULTIVO, "2026/2027", null)), List.of(), List.of());
        when(recomendacaoRepository.ultimasDasFazendas(any())).thenReturn(List.of(
                recomendacao(talhaoA, SituacaoIrrigacao.IRRIGAR),
                recomendacao(talhaoB, SituacaoIrrigacao.IRRIGAR)));   // B não está em cultivo

        var irrigacao = resumoService.resumoDaFazenda(1L).irrigacao();

        assertEquals(1, irrigacao.irrigar());
        assertEquals(0, irrigacao.naoIrrigar());
        assertEquals(0, irrigacao.semDadosSuficientes());
    }

    @Test
    void resumoGeralSomaAIrrigacaoDasFazendas() {
        when(fazendaRepository.findAllByUsuario_Id(7L)).thenReturn(List.of(fazenda));
        when(talhaoRepository.findAllByFazenda_IdIn(any())).thenReturn(List.of(talhaoA, talhaoB));
        when(plantioRepository.buscarPorFazendas(any())).thenReturn(List.of(
                plantio(talhaoA, StatusPlantio.EM_CULTIVO, "2026/2027", null),
                plantio(talhaoB, StatusPlantio.EM_CULTIVO, "2026/2027", null)));
        when(sensorRepository.listarPorFazendas(any())).thenReturn(List.of());
        when(recomendacaoRepository.ultimasDasFazendas(any())).thenReturn(List.of(
                recomendacao(talhaoA, SituacaoIrrigacao.IRRIGAR),
                recomendacao(talhaoB, SituacaoIrrigacao.AGUARDAR_CHUVA)));

        ResumoGeralResponse geral = resumoService.resumoGeral(7L);

        assertEquals(1, geral.totais().irrigacao().irrigar());
        assertEquals(1, geral.totais().irrigacao().aguardarChuva());
        assertEquals(1, geral.fazendas().get(0).irrigacao().irrigar());
    }
}
