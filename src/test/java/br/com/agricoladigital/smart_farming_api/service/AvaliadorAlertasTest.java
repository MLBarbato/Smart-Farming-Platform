package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.*;
import br.com.agricoladigital.smart_farming_api.service.ConciliadorAlertas.Acao;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvaliadorAlertasTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);
    private static final LocalDate HOJE = AGORA.toLocalDate();

    @Mock private FazendaRepository fazendaRepository;
    @Mock private TalhaoRepository talhaoRepository;
    @Mock private PlantioRepository plantioRepository;
    @Mock private SensorRepository sensorRepository;
    @Mock private EstacaoMeteorologicaRepository estacaoRepository;
    @Mock private PrevisaoTempoRepository previsaoRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private RegraAlertaRepository regraRepository;
    @Mock private AlertaRepository alertaRepository;

    private AvaliadorAlertas avaliador;
    private Fazenda fazenda;
    private Talhao talhao;
    private final List<Alerta> salvos = new ArrayList<>();
    private List<Alerta> abertos = new ArrayList<>();
    private List<Alerta> fechados = new ArrayList<>();
    private List<Sensor> sensoresTalhao = new ArrayList<>();
    private List<Leitura> ultimas = new ArrayList<>();
    private List<PrevisaoTempo> previsao = new ArrayList<>();
    private EstacaoMeteorologica estacao;
    private List<Sensor> sensoresEstacao = new ArrayList<>();
    private List<RegraAlerta> regras;
    private boolean emCultivo = true;

    @BeforeEach
    void preparar() {
        avaliador = new AvaliadorAlertas(fazendaRepository, talhaoRepository, plantioRepository, sensorRepository,
                estacaoRepository, previsaoRepository, leituraRepository, regraRepository, alertaRepository,
                new ConciliadorAlertas(alertaRepository), 12, 6,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("Santa Maria", null, 100.0, -17.0, -50.0, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        talhao = new Talhao("A1", 10.0, null, fazenda);
        ReflectionTestUtils.setField(talhao, "id", 10L);
        regras = regrasPadrao();

        lenient().when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        lenient().when(talhaoRepository.findAllByFazenda_Id(1L)).thenReturn(List.of(talhao));
        lenient().when(plantioRepository.buscarPorFazendaEStatus(1L, StatusPlantio.EM_CULTIVO)).thenAnswer(i -> {
            if (!emCultivo) {
                return List.of();
            }
            Plantio plantio = new Plantio(talhao, new Cultura("Soja", 120), "Direto", "2026/2027", HOJE.minusDays(30), null);
            return List.of(plantio);
        });
        lenient().when(sensorRepository.listarPorFazenda(1L)).thenAnswer(i -> sensoresTalhao);
        lenient().when(estacaoRepository.buscarDaFazenda(1L)).thenAnswer(i -> Optional.ofNullable(estacao));
        lenient().when(sensorRepository.listarDaEstacao(100L)).thenAnswer(i -> sensoresEstacao);
        lenient().when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenAnswer(i -> previsao);
        lenient().when(regraRepository.findAll()).thenAnswer(i -> regras);
        lenient().when(alertaRepository.buscarDaFazendaPorStatus(eq(1L), any())).thenAnswer(i -> abertos);
        lenient().when(alertaRepository.buscarFechadosDesde(eq(1L), any(), any())).thenAnswer(i -> fechados);
        lenient().when(leituraRepository.ultimasPorDispositivos(any())).thenAnswer(i -> ultimas);
        lenient().when(alertaRepository.saveAndFlush(any(Alerta.class))).thenAnswer(i -> {
            salvos.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    private List<RegraAlerta> regrasPadrao() {
        List<RegraAlerta> r = new ArrayList<>();
        r.add(regra(EventoAlerta.SECA, CriticidadeAlerta.BAIXA, OperadorRegra.MENOR_QUE, VariavelMonitorada.UMIDADE_SOLO, 30, "%", 3));
        r.add(regra(EventoAlerta.SECA, CriticidadeAlerta.MEDIA, OperadorRegra.MENOR_QUE, VariavelMonitorada.UMIDADE_SOLO, 25, "%", 3));
        r.add(regra(EventoAlerta.SECA, CriticidadeAlerta.ALTA, OperadorRegra.MENOR_QUE, VariavelMonitorada.UMIDADE_SOLO, 20, "%", 3));
        r.add(regra(EventoAlerta.EXCESSO_PRECIPITACAO, CriticidadeAlerta.BAIXA, OperadorRegra.MAIOR_OU_IGUAL, VariavelMonitorada.PRECIPITACAO, 30, "mm", 24));
        r.add(regra(EventoAlerta.EXCESSO_PRECIPITACAO, CriticidadeAlerta.MEDIA, OperadorRegra.MAIOR_OU_IGUAL, VariavelMonitorada.PRECIPITACAO, 50, "mm", 24));
        r.add(regra(EventoAlerta.EXCESSO_PRECIPITACAO, CriticidadeAlerta.ALTA, OperadorRegra.MAIOR_OU_IGUAL, VariavelMonitorada.PRECIPITACAO, 100, "mm", 24));
        r.add(regra(EventoAlerta.RISCO_GEADA, CriticidadeAlerta.BAIXA, OperadorRegra.MENOR_OU_IGUAL, VariavelMonitorada.TEMPERATURA_AR, 5, "°C", 72));
        r.add(regra(EventoAlerta.RISCO_GEADA, CriticidadeAlerta.MEDIA, OperadorRegra.MENOR_OU_IGUAL, VariavelMonitorada.TEMPERATURA_AR, 3, "°C", 72));
        r.add(regra(EventoAlerta.RISCO_GEADA, CriticidadeAlerta.ALTA, OperadorRegra.MENOR_OU_IGUAL, VariavelMonitorada.TEMPERATURA_AR, 1, "°C", 72));
        r.add(regra(EventoAlerta.FALHA_SENSOR, CriticidadeAlerta.MEDIA, OperadorRegra.MAIOR_QUE, null, 30, "min", null));
        r.add(regra(EventoAlerta.FALHA_SENSOR, CriticidadeAlerta.ALTA, OperadorRegra.MAIOR_QUE, null, 360, "min", null));
        return r;
    }

    private RegraAlerta regra(EventoAlerta e, CriticidadeAlerta c, OperadorRegra op, VariavelMonitorada v,
                              double limiar, String unidade, Integer janela) {
        RegraAlerta regra = new RegraAlerta(e, c, op, v, limiar, unidade, janela, "d");
        ReflectionTestUtils.setField(regra, "id", (long) (regras == null ? 0 : regras.size()) + 1);
        return regra;
    }

    private Sensor sensorTalhao(long id, VariavelMonitorada variavel, StatusDispositivo status) {
        Dispositivo d = new Dispositivo("S-" + id, "SENSOR", null, status, talhao);
        ReflectionTestUtils.setField(d, "id", id);
        Sensor sensor = new Sensor(d, variavel);
        ReflectionTestUtils.setField(sensor, "id", id);
        sensoresTalhao.add(sensor);
        return sensor;
    }

    private void estacaoAtiva(long ultimaSincronizacaoMinutosAtras) {
        Dispositivo d = new Dispositivo("EST", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        ReflectionTestUtils.setField(d, "id", 100L);
        estacao = new EstacaoMeteorologica(d, "m", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 100L);
        estacao.registrarSincronizacao(AGORA.minusMinutes(ultimaSincronizacaoMinutosAtras));
    }

    private Sensor sensorEstacao(long id, VariavelMonitorada variavel) {
        Dispositivo d = new Dispositivo("EST-" + id, "SENSOR", null, StatusDispositivo.ATIVO, fazenda);
        ReflectionTestUtils.setField(d, "id", id);
        Sensor sensor = new Sensor(d, variavel, estacao);
        ReflectionTestUtils.setField(sensor, "id", id);
        sensoresEstacao.add(sensor);
        return sensor;
    }

    private Leitura leitura(Sensor sensor, double valor, LocalDateTime dataHora) {
        Leitura l = new Leitura(sensor.getDispositivo(), valor, dataHora, OrigemLeitura.SIMULADA);
        ReflectionTestUtils.setField(l, "id", sensor.getId() * 1000);
        return l;
    }

    private void previsao(int diasAPartirDeHoje, Double minima, Double chuva, LocalDateTime consultadaEm) {
        previsao.add(new PrevisaoTempo(fazenda, HOJE.plusDays(diasAPartirDeHoje), 20.0, minima, 30.0, chuva, 50,
                "Open-Meteo", consultadaEm));
    }

    private Alerta alertaAberto(EventoAlerta evento, CriticidadeAlerta criticidade) {
        RegraAlerta regra = regras.stream()
                .filter(r -> r.getEvento() == evento && r.getCriticidade() == criticidade).findFirst().orElseThrow();
        Alerta alerta = new Alerta(regra, talhao, null, AGORA.minusHours(2), "antigo");
        abertos.add(alerta);
        return alerta;
    }

    private Alerta unico(EventoAlerta evento) {
        List<Alerta> doEvento = salvos.stream().filter(a -> a.getEvento() == evento).toList();
        assertEquals(1, doEvento.size(), "alertas criados de " + evento);
        return doEvento.get(0);
    }

    private void soloMedio(double media) {
        Sensor solo = sensorTalhao(1, VariavelMonitorada.UMIDADE_SOLO, StatusDispositivo.ATIVO);
        lenient().when(leituraRepository.mediaNoIntervalo(eq(List.of(1L)), any(), any())).thenReturn(media);
        lenient().when(leituraRepository.findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(
                eq(List.of(1L)), any(), any())).thenReturn(Optional.of(leitura(solo, media, AGORA.minusMinutes(5))));
        ultimas.add(leitura(solo, media, AGORA.minusMinutes(5)));
    }

    // ---- SECA ----

    @Test
    void secaCriaAlertaNaCriticidadeMaisSeveraSatisfeita() {
        soloMedio(18.0);
        Map<Acao, Integer> acoes = avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.SECA);
        assertEquals(CriticidadeAlerta.ALTA, alerta.getCriticidade());
        assertEquals(1, acoes.get(Acao.CRIADO));
        assertNotNull(alerta.getLeitura());
        assertTrue(alerta.getMensagem().contains("18"), alerta.getMensagem());
        assertTrue(alerta.getMensagem().contains("sensor do talhão"), alerta.getMensagem());
        assertSame(talhao, alerta.getTalhao());
    }

    @Test
    void secaEmFaixasIntermediarias() {
        soloMedio(28.0);
        avaliador.avaliarFazenda(1L);
        assertEquals(CriticidadeAlerta.BAIXA, unico(EventoAlerta.SECA).getCriticidade());
    }

    @Test
    void secaNaoDisparaComUmidadeNormal() {
        soloMedio(45.0);
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
    }

    @Test
    void secaUsaALimiarExatoComoNaoSatisfeitoParaMenorQue() {
        soloMedio(30.0);
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
    }

    @Test
    void secaUsaAEstacaoQuandoOTalhaoNaoTemSensorDeSolo() {
        estacaoAtiva(10);
        Sensor solo = sensorEstacao(201, VariavelMonitorada.UMIDADE_SOLO);
        when(leituraRepository.mediaNoIntervalo(eq(List.of(201L)), any(), any())).thenReturn(22.0);
        when(leituraRepository.findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(
                eq(List.of(201L)), any(), any())).thenReturn(Optional.of(leitura(solo, 22.0, AGORA.minusMinutes(30))));

        avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.SECA);
        assertEquals(CriticidadeAlerta.MEDIA, alerta.getCriticidade());
        assertTrue(alerta.getMensagem().contains("estação da fazenda"), alerta.getMensagem());
    }

    @Test
    void secaIgnoraSensorDeSoloInativo() {
        sensorTalhao(1, VariavelMonitorada.UMIDADE_SOLO, StatusDispositivo.INATIVO);
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
        verify(leituraRepository, never()).mediaNoIntervalo(any(), any(), any());
    }

    @Test
    void semLeiturasNaJanelaNaoCriaENaoNormalizaAlertaAberto() {
        sensorTalhao(1, VariavelMonitorada.UMIDADE_SOLO, StatusDispositivo.ATIVO);
        when(leituraRepository.mediaNoIntervalo(any(), any(), any())).thenReturn(null);
        Alerta existente = alertaAberto(EventoAlerta.SECA, CriticidadeAlerta.MEDIA);

        avaliador.avaliarFazenda(1L);

        assertTrue(salvos.isEmpty());
        assertEquals(StatusAlerta.ABERTO, existente.getStatus());
        assertNull(existente.getNormalizadoEm());
    }

    @Test
    void talhaoSemPlantioEmCultivoNaoGeraAlertaEResolveOAbertoAutomaticamente() {
        emCultivo = false;
        soloMedio(10.0);
        Alerta existente = alertaAberto(EventoAlerta.SECA, CriticidadeAlerta.ALTA);

        Map<Acao, Integer> acoes = avaliador.avaliarFazenda(1L);

        assertTrue(salvos.isEmpty());
        assertEquals(StatusAlerta.RESOLVIDO, existente.getStatus());
        assertEquals(ResolucaoAlerta.AUTOMATICA, existente.getResolucao());
        assertEquals(1, acoes.get(Acao.RESOLVIDO_AUTOMATICAMENTE));
    }

    @Test
    void regraDesativadaNaoEAvaliada() {
        soloMedio(10.0);
        regras.stream().filter(r -> r.getEvento() == EventoAlerta.SECA)
                .forEach(r -> ReflectionTestUtils.setField(r, "ativa", false));
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
    }

    @Test
    void naoDuplicaAlertaAbertoDoMesmoNivel() {
        soloMedio(28.0);
        alertaAberto(EventoAlerta.SECA, CriticidadeAlerta.BAIXA);
        Map<Acao, Integer> acoes = avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
        assertNull(acoes.get(Acao.CRIADO));
    }

    @Test
    void pioraSobeACriticidadeDoAlertaExistente() {
        soloMedio(18.0);
        Alerta existente = alertaAberto(EventoAlerta.SECA, CriticidadeAlerta.BAIXA);
        Map<Acao, Integer> acoes = avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
        assertEquals(CriticidadeAlerta.ALTA, existente.getCriticidade());
        assertEquals(1, acoes.get(Acao.ESCALADO));
    }

    @Test
    void normalizacaoFechaAlertaNaoAssumidoEManteMEmAtendimento() {
        soloMedio(45.0);
        Alerta naoAssumido = alertaAberto(EventoAlerta.SECA, CriticidadeAlerta.BAIXA);
        avaliador.avaliarFazenda(1L);
        assertEquals(StatusAlerta.RESOLVIDO, naoAssumido.getStatus());
        assertEquals(ResolucaoAlerta.AUTOMATICA, naoAssumido.getResolucao());

        abertos.clear();
        Alerta emAtendimento = alertaAberto(EventoAlerta.SECA, CriticidadeAlerta.BAIXA);
        ReflectionTestUtils.setField(emAtendimento, "status", StatusAlerta.EM_ATENDIMENTO);
        avaliador.avaliarFazenda(1L);
        assertEquals(StatusAlerta.EM_ATENDIMENTO, emAtendimento.getStatus());
        assertEquals(AGORA, emAtendimento.getNormalizadoEm());
    }

    @Test
    void naoReabreDentroDoPeriodoDeEsperaMasReabreSeForMaisGrave() {
        soloMedio(28.0);
        RegraAlerta baixa = regras.get(0);
        Alerta fechado = new Alerta(baixa, talhao, null, AGORA.minusHours(5), "x");
        fechado.resolverAutomaticamente(AGORA.minusHours(4));
        fechados.add(fechado);

        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());

        when(leituraRepository.mediaNoIntervalo(eq(List.of(1L)), any(), any())).thenReturn(18.0);
        avaliador.avaliarFazenda(1L);
        assertEquals(CriticidadeAlerta.ALTA, unico(EventoAlerta.SECA).getCriticidade());
    }

    // ---- EXCESSO_PRECIPITACAO ----

    @Test
    void excessoUsaChuvaAcumuladaDaEstacao() {
        estacaoAtiva(10);
        Sensor chuva = sensorEstacao(202, VariavelMonitorada.PRECIPITACAO);
        when(leituraRepository.somaNoIntervalo(eq(List.of(202L)), any(), any())).thenReturn(55.0);
        when(leituraRepository.findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(
                eq(List.of(202L)), any(), any())).thenReturn(Optional.of(leitura(chuva, 5.0, AGORA.minusMinutes(60))));

        avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.EXCESSO_PRECIPITACAO);
        assertEquals(CriticidadeAlerta.MEDIA, alerta.getCriticidade());
        assertTrue(alerta.getMensagem().contains("55"), alerta.getMensagem());
        assertTrue(alerta.getMensagem().contains("acumulados"), alerta.getMensagem());
    }

    @Test
    void excessoUsaPrevisaoAtualEDeixaClaroQueEPrevisao() {
        previsao(0, 15.0, 10.0, AGORA.minusHours(1));
        previsao(1, 15.0, 120.0, AGORA.minusHours(1));

        avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.EXCESSO_PRECIPITACAO);
        assertEquals(CriticidadeAlerta.ALTA, alerta.getCriticidade());
        assertTrue(alerta.getMensagem().contains("previs"), alerta.getMensagem());
        assertTrue(alerta.getMensagem().contains("Open-Meteo"), alerta.getMensagem());
        assertNull(alerta.getLeitura());
    }

    @Test
    void excessoIgnoraPrevisaoDesatualizada() {
        previsao(0, 15.0, 120.0, AGORA.minusHours(13));
        Alerta existente = alertaAberto(EventoAlerta.EXCESSO_PRECIPITACAO, CriticidadeAlerta.ALTA);

        avaliador.avaliarFazenda(1L);

        assertTrue(salvos.isEmpty());
        assertEquals(StatusAlerta.ABERTO, existente.getStatus(), "sem dado atual, o alerta não é normalizado");
    }

    @Test
    void excessoIgnoraDiasForaDeHojeEAmanha() {
        previsao(2, 15.0, 200.0, AGORA.minusHours(1));
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
    }

    // ---- RISCO_GEADA ----

    @Test
    void geadaUsaMinimaPrevistaNosProximosTresDias() {
        previsao(0, 12.0, 0.0, AGORA.minusHours(1));
        previsao(2, 2.0, 0.0, AGORA.minusHours(1));
        previsao(3, -2.0, 0.0, AGORA.minusHours(1));

        avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.RISCO_GEADA);
        assertEquals(CriticidadeAlerta.MEDIA, alerta.getCriticidade());
        assertTrue(alerta.getMensagem().contains("Risco de geada (previsão)"), alerta.getMensagem());
        assertTrue(alerta.getMensagem().contains("2"), alerta.getMensagem());
    }

    @Test
    void geadaSemRiscoQuandoAMinimaEAlta() {
        previsao(0, 12.0, 0.0, AGORA.minusHours(1));
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
    }

    @Test
    void geadaComEstacaoJaFriaEAltaMesmoSemPrevisao() {
        estacaoAtiva(10);
        Sensor temperatura = sensorEstacao(203, VariavelMonitorada.TEMPERATURA_AR);
        when(leituraRepository.findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(
                eq(List.of(203L)), any(), any())).thenReturn(Optional.of(leitura(temperatura, 2.5, AGORA.minusMinutes(30))));

        avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.RISCO_GEADA);
        assertEquals(CriticidadeAlerta.ALTA, alerta.getCriticidade());
        assertNotNull(alerta.getLeitura());
        assertTrue(alerta.getMensagem().contains("estação"), alerta.getMensagem());
    }

    @Test
    void geadaSemPrevisaoAtualENemEstacaoNaoNormalizaAlertaAberto() {
        previsao(0, -3.0, 0.0, AGORA.minusHours(20));
        Alerta existente = alertaAberto(EventoAlerta.RISCO_GEADA, CriticidadeAlerta.ALTA);

        avaliador.avaliarFazenda(1L);

        assertTrue(salvos.isEmpty());
        assertEquals(StatusAlerta.ABERTO, existente.getStatus());
    }

    // ---- FALHA_SENSOR ----

    @Test
    void sensorAtivoSemLeituraHaMaisDe30MinutosGeraAlertaMedia() {
        Sensor sensor = sensorTalhao(1, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        ultimas.add(leitura(sensor, 20.0, AGORA.minusMinutes(45)));

        avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.FALHA_SENSOR);
        assertEquals(CriticidadeAlerta.MEDIA, alerta.getCriticidade());
        assertTrue(alerta.getMensagem().contains("S-1"), alerta.getMensagem());
        assertTrue(alerta.getMensagem().contains("45 min"), alerta.getMensagem());
    }

    @Test
    void sensorSemLeituraHaMaisDeSeisHorasGeraAlertaAlta() {
        Sensor sensor = sensorTalhao(1, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        ultimas.add(leitura(sensor, 20.0, AGORA.minusHours(7)));

        avaliador.avaliarFazenda(1L);

        assertEquals(CriticidadeAlerta.ALTA, unico(EventoAlerta.FALHA_SENSOR).getCriticidade());
    }

    @Test
    void sensorComLeituraRecenteResolveOAlertaDeFalha() {
        Sensor sensor = sensorTalhao(1, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        ultimas.add(leitura(sensor, 20.0, AGORA.minusMinutes(10)));
        Alerta existente = alertaAberto(EventoAlerta.FALHA_SENSOR, CriticidadeAlerta.MEDIA);

        avaliador.avaliarFazenda(1L);

        assertTrue(salvos.isEmpty());
        assertEquals(StatusAlerta.RESOLVIDO, existente.getStatus());
    }

    @Test
    void sensorInativoNaoGeraFalha() {
        Sensor sensor = sensorTalhao(1, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.MANUTENCAO);
        ultimas.add(leitura(sensor, 20.0, AGORA.minusHours(9)));
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
    }

    @Test
    void falhaVaiAoTalhaoMesmoSemPlantioEmCultivo() {
        emCultivo = false;
        Sensor sensor = sensorTalhao(1, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        ultimas.add(leitura(sensor, 20.0, AGORA.minusHours(2)));
        avaliador.avaliarFazenda(1L);
        assertEquals(CriticidadeAlerta.MEDIA, unico(EventoAlerta.FALHA_SENSOR).getCriticidade());
    }

    @Test
    void sensorQueNuncaLeuTemToleranciaNoDiaDaInstalacao() {
        Sensor novo = sensorTalhao(1, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        ReflectionTestUtils.setField(novo.getDispositivo(), "dataInstalacao", HOJE);
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());

        ReflectionTestUtils.setField(novo.getDispositivo(), "dataInstalacao", HOJE.minusDays(2));
        avaliador.avaliarFazenda(1L);
        assertEquals(CriticidadeAlerta.ALTA, unico(EventoAlerta.FALHA_SENSOR).getCriticidade());
    }

    @Test
    void estacaoDesatualizadaGeraFalhaParaOsTalhoesEmCultivo() {
        estacaoAtiva(150);   // sincronizada há 150 min; limite é 2 x 60 = 120
        avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.FALHA_SENSOR);
        assertEquals(CriticidadeAlerta.MEDIA, alerta.getCriticidade());
        assertTrue(alerta.getMensagem().contains("estação da fazenda"), alerta.getMensagem());
    }

    @Test
    void estacaoEmDiaNaoGeraFalha() {
        estacaoAtiva(50);
        avaliador.avaliarFazenda(1L);
        assertTrue(salvos.isEmpty());
    }

    @Test
    void falhaDeVariasFontesGeraUmSoAlertaComAPior() {
        Sensor a = sensorTalhao(1, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        Sensor b = sensorTalhao(2, VariavelMonitorada.UMIDADE_AR, StatusDispositivo.ATIVO);
        ultimas.add(leitura(a, 20.0, AGORA.minusMinutes(40)));
        ultimas.add(leitura(b, 20.0, AGORA.minusMinutes(100)));

        avaliador.avaliarFazenda(1L);

        Alerta alerta = unico(EventoAlerta.FALHA_SENSOR);
        assertTrue(alerta.getMensagem().contains("S-2"), alerta.getMensagem());
        assertTrue(alerta.getMensagem().contains("mais 1"), alerta.getMensagem());
    }

    // ---- geral ----

    @Test
    void fazendaInexistenteOuSemTalhoesNaoFazNada() {
        when(fazendaRepository.findById(2L)).thenReturn(Optional.empty());
        assertTrue(avaliador.avaliarFazenda(2L).isEmpty());

        when(talhaoRepository.findAllByFazenda_Id(1L)).thenReturn(List.of());
        assertTrue(avaliador.avaliarFazenda(1L).isEmpty());
        verifyNoInteractions(alertaRepository);
    }

    @Test
    void mensagemNuncaExcedeOLimiteDaColuna() {
        soloMedio(10.0);
        avaliador.avaliarFazenda(1L);
        assertTrue(unico(EventoAlerta.SECA).getMensagem().length() <= 500);
    }
}
