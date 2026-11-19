package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.*;
import br.com.agricoladigital.smart_farming_api.service.RecomendadorIrrigacao.ResumoCalculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecomendadorIrrigacaoTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);
    private static final LocalDate HOJE = AGORA.toLocalDate();

    @Mock private FazendaRepository fazendaRepository;
    @Mock private TalhaoRepository talhaoRepository;
    @Mock private PlantioRepository plantioRepository;
    @Mock private SensorRepository sensorRepository;
    @Mock private EstacaoMeteorologicaRepository estacaoRepository;
    @Mock private PrevisaoTempoRepository previsaoRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private RecomendacaoIrrigacaoRepository recomendacaoRepository;

    private RecomendadorIrrigacao recomendador;
    private Fazenda fazenda;
    private Talhao talhao;
    private Cultura soja;
    private final List<RecomendacaoIrrigacao> salvas = new ArrayList<>();
    private final List<RecomendacaoIrrigacao> anteriores = new ArrayList<>();
    private final List<Sensor> sensores = new ArrayList<>();
    private final List<PrevisaoTempo> previsao = new ArrayList<>();
    private boolean emCultivo = true;

    @BeforeEach
    void preparar() {
        recomendador = new RecomendadorIrrigacao(fazendaRepository, talhaoRepository, plantioRepository,
                sensorRepository, estacaoRepository, previsaoRepository, leituraRepository, recomendacaoRepository,
                new CalculadoraIrrigacao(0.8, 35, 0.2, 30), 12, 6, 3,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("F", null, 10.0, -17.0, -50.0, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        talhao = new Talhao("T1", 5.0, null, fazenda);
        ReflectionTestUtils.setField(talhao, "id", 10L);
        soja = new Cultura("Soja", 120, 30.0, 38.0, 30);

        lenient().when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        lenient().when(plantioRepository.buscarPorFazendaEStatus(1L, StatusPlantio.EM_CULTIVO)).thenAnswer(i ->
                emCultivo ? List.of(new Plantio(talhao, soja, "Direto", "2026/2027", HOJE.minusDays(30), null)) : List.of());
        lenient().when(sensorRepository.listarPorFazenda(1L)).thenAnswer(i -> sensores);
        lenient().when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.empty());
        lenient().when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenAnswer(i -> previsao);
        lenient().when(recomendacaoRepository.ultimasDaFazenda(1L)).thenAnswer(i -> anteriores);
        lenient().when(recomendacaoRepository.save(any(RecomendacaoIrrigacao.class))).thenAnswer(i -> {
            salvas.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    private void soloDoTalhao(Double media) {
        Dispositivo d = new Dispositivo("SOLO", "SENSOR", null, StatusDispositivo.ATIVO, talhao);
        ReflectionTestUtils.setField(d, "id", 20L);
        Sensor sensor = new Sensor(d, VariavelMonitorada.UMIDADE_SOLO);
        ReflectionTestUtils.setField(sensor, "id", 20L);
        sensores.add(sensor);
        lenient().when(leituraRepository.mediaNoIntervalo(eq(List.of(20L)), any(), any())).thenReturn(media);
    }

    private void previsaoDoDia(int dia, Double chuva, Double tmax, LocalDateTime consultadaEm) {
        previsao.add(new PrevisaoTempo(fazenda, HOJE.plusDays(dia), 22.0, 15.0, tmax, chuva, 40, "Open-Meteo", consultadaEm));
    }

    private RecomendacaoIrrigacao anterior(SituacaoIrrigacao situacao, LocalDateTime validoAte, List<String> faltantes) {
        return anterior(situacao, validoAte, faltantes,
                situacao == SituacaoIrrigacao.SEM_DADOS_SUFICIENTES ? null : OrigemUmidade.SENSOR_TALHAO);
    }

    private RecomendacaoIrrigacao anterior(
            SituacaoIrrigacao situacao, LocalDateTime validoAte, List<String> faltantes, OrigemUmidade origem
    ) {
        InsumosIrrigacao insumos = new InsumosIrrigacao("Soja", 30.0, 38.0, 30, origem == null ? null : 36.0, origem,
                null, null, null, null, false, faltantes);
        RecomendacaoIrrigacao r = new RecomendacaoIrrigacao(talhao, null, AGORA.minusHours(2), validoAte, situacao,
                situacao == SituacaoIrrigacao.IRRIGAR ? 10.0 : null, "x", insumos);
        ReflectionTestUtils.setField(r, "id", 1L);
        anteriores.add(r);
        return r;
    }

    @Test
    void gravaARecomendacaoComOsInsumosDoCalculo() {
        soloDoTalhao(28.0);
        previsaoDoDia(0, 3.0, 29.0, AGORA.minusHours(1));
        previsaoDoDia(1, 2.0, 31.0, AGORA.minusHours(1));

        ResumoCalculo resumo = recomendador.calcularFazenda(1L);

        assertEquals(1, resumo.calculadas());
        assertEquals(1, resumo.gravadas());
        RecomendacaoIrrigacao r = salvas.get(0);
        assertEquals(SituacaoIrrigacao.IRRIGAR, r.getSituacao());
        assertEquals(26.0, r.getVolumeRecomendadoMm());      // 30 mm − 0,8 × 5 mm
        assertEquals(28.0, r.getUmidadeAtual());
        assertEquals(OrigemUmidade.SENSOR_TALHAO, r.getOrigemUmidade());
        assertEquals(5.0, r.getChuvaPrevistaMm());
        assertEquals(31.0, r.getTemperaturaMaximaPrevista());
        assertEquals("Soja", r.getCulturaNome());
        assertEquals(30.0, r.getUmidadeMinima());
        assertEquals(30, r.getProfundidadeRaizCm());
        assertEquals("Open-Meteo", r.getPrevisaoFonte());
        assertEquals(AGORA.minusHours(1), r.getPrevisaoConsultadaEm());
        assertEquals(AGORA, r.getDataHora());
        assertEquals(AGORA.plusHours(6), r.getValidoAte());
        assertSame(talhao, r.getTalhao());
    }

    @Test
    void usaApenasHojeEAmanhaNaChuvaEQuandoNaoHaPrevisaoAtualNaoRecomendaSoloSeco() {
        soloDoTalhao(28.0);
        previsaoDoDia(0, 1.0, 29.0, AGORA.minusHours(13));   // vencida (> 12 h)
        recomendador.calcularFazenda(1L);

        RecomendacaoIrrigacao r = salvas.get(0);
        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, r.getSituacao());
        assertNull(r.getVolumeRecomendadoMm());
        assertTrue(r.listaDadosFaltantes().get(0).contains("previsão"));
        assertNull(r.getChuvaPrevistaMm(), "previsão vencida não é usada");
    }

    @Test
    void ignoraDiasDepoisDeAmanha() {
        soloDoTalhao(28.0);
        previsaoDoDia(0, 1.0, 29.0, AGORA.minusHours(1));
        previsaoDoDia(2, 80.0, 40.0, AGORA.minusHours(1));

        recomendador.calcularFazenda(1L);

        assertEquals(1.0, salvas.get(0).getChuvaPrevistaMm());
        assertEquals(29.0, salvas.get(0).getTemperaturaMaximaPrevista());
    }

    @Test
    void semLeituraDeUmidadeGravaSemDadosSuficientes() {
        soloDoTalhao(null);
        previsaoDoDia(0, 1.0, 29.0, AGORA.minusHours(1));

        recomendador.calcularFazenda(1L);

        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, salvas.get(0).getSituacao());
        assertTrue(salvas.get(0).listaDadosFaltantes().get(0).contains("umidade do solo"));
    }

    @Test
    void semSensorDeSoloNoTalhaoUsaAEstacaoDaFazenda() {
        Dispositivo dEstacao = new Dispositivo("EST", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        EstacaoMeteorologica estacao = new EstacaoMeteorologica(dEstacao, "m", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 50L);
        Sensor solo = new Sensor(new Dispositivo("EST-S", "SENSOR", null, StatusDispositivo.ATIVO, fazenda),
                VariavelMonitorada.UMIDADE_SOLO, estacao);
        ReflectionTestUtils.setField(solo, "id", 51L);
        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.of(estacao));
        when(sensorRepository.listarDaEstacao(50L)).thenReturn(List.of(solo));
        when(leituraRepository.mediaNoIntervalo(eq(List.of(51L)), any(), any())).thenReturn(36.0);

        recomendador.calcularFazenda(1L);

        assertEquals(SituacaoIrrigacao.NAO_IRRIGAR, salvas.get(0).getSituacao());
        assertEquals(OrigemUmidade.ESTACAO, salvas.get(0).getOrigemUmidade());
    }

    @Test
    void sensorDeSoloInativoNaoE_usado() {
        soloDoTalhao(28.0);
        ReflectionTestUtils.setField(sensores.get(0).getDispositivo(), "status", StatusDispositivo.INATIVO);

        recomendador.calcularFazenda(1L);

        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, salvas.get(0).getSituacao());
        verify(leituraRepository, never()).mediaNoIntervalo(any(), any(), any());
    }

    @Test
    void talhaoSemPlantioEmCultivoNaoGeraRecomendacao() {
        emCultivo = false;
        assertEquals(new ResumoCalculo(0, 0), recomendador.calcularFazenda(1L));
        verify(recomendacaoRepository, never()).save(any());
    }

    @Test
    void fazendaInexistenteNaoFazNada() {
        when(fazendaRepository.findById(9L)).thenReturn(Optional.empty());
        assertEquals(new ResumoCalculo(0, 0), recomendador.calcularFazenda(9L));
    }

    @Test
    void naoGravaDeNovoQuandoOResultadoNaoMudouEAAnteriorAindaValeu() {
        soloDoTalhao(36.0);
        anterior(SituacaoIrrigacao.NAO_IRRIGAR, AGORA.plusHours(2), List.of());

        ResumoCalculo resumo = recomendador.calcularFazenda(1L);

        assertEquals(1, resumo.calculadas());
        assertEquals(0, resumo.gravadas());
        assertTrue(salvas.isEmpty());
    }

    @Test
    void gravaDeNovoQuandoAOrigemDaUmidadeMudaMesmoComASituacaoIgual() {
        soloDoTalhao(36.0);   // agora o talhão tem sensor; a recomendação vigente usava a estação
        anterior(SituacaoIrrigacao.NAO_IRRIGAR, AGORA.plusHours(2), List.of(), OrigemUmidade.ESTACAO);

        assertEquals(1, recomendador.calcularFazenda(1L).gravadas());
        assertEquals(OrigemUmidade.SENSOR_TALHAO, salvas.get(0).getOrigemUmidade());
    }

    @Test
    void gravaDeNovoQuandoASituacaoMuda() {
        soloDoTalhao(36.0);
        anterior(SituacaoIrrigacao.IRRIGAR, AGORA.plusHours(2), List.of());

        assertEquals(1, recomendador.calcularFazenda(1L).gravadas());
        assertEquals(SituacaoIrrigacao.NAO_IRRIGAR, salvas.get(0).getSituacao());
    }

    @Test
    void gravaDeNovoQuandoAAnteriorVenceu() {
        soloDoTalhao(36.0);
        anterior(SituacaoIrrigacao.NAO_IRRIGAR, AGORA, List.of());   // venceu agora

        assertEquals(1, recomendador.calcularFazenda(1L).gravadas());
    }

    @Test
    void semDadosSoGravaDeNovoSeOQueFaltaMudou() {
        soloDoTalhao(null);
        anterior(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, AGORA.plusHours(2),
                List.of("leitura de umidade do solo nas últimas 3 h"));
        assertEquals(0, recomendador.calcularFazenda(1L).gravadas());

        anteriores.clear();
        anterior(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, AGORA.plusHours(2), List.of("previsão do tempo atualizada"));
        assertEquals(1, recomendador.calcularFazenda(1L).gravadas());
    }

    @Test
    void configuracaoInvalidaEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> new RecomendadorIrrigacao(fazendaRepository, talhaoRepository,
                plantioRepository, sensorRepository, estacaoRepository, previsaoRepository, leituraRepository,
                recomendacaoRepository, new CalculadoraIrrigacao(0.8, 35, 0.2, 30), 12, 0, 3, Clock.systemUTC()));
    }
}
