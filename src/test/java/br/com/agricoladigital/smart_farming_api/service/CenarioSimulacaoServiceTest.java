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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CenarioSimulacaoServiceTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);

    @Mock private FazendaRepository fazendaRepository;
    @Mock private SensorRepository sensorRepository;
    @Mock private EstacaoMeteorologicaRepository estacaoRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private LeituraService leituraService;
    @Mock private AvaliadorAlertas avaliador;

    private final EstadoSimulacao estado = new EstadoSimulacao();
    private CenarioSimulacaoService service;
    private Fazenda fazenda;
    private Sensor solo;
    private Sensor temperaturaEstacao;
    private EstacaoMeteorologica estacao;

    @BeforeEach
    void preparar() {
        service = new CenarioSimulacaoService(estado, fazendaRepository, sensorRepository, estacaoRepository,
                leituraRepository, leituraService, avaliador, 6,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("F", null, 10.0, null, null, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        Talhao talhao = new Talhao("T1", 5.0, null, fazenda);
        ReflectionTestUtils.setField(talhao, "id", 10L);
        solo = new Sensor(new Dispositivo("SOLO", "SENSOR", null, StatusDispositivo.ATIVO, talhao),
                VariavelMonitorada.UMIDADE_SOLO);
        ReflectionTestUtils.setField(solo, "id", 20L);
        lenient().when(fazendaRepository.existsById(1L)).thenReturn(true);
        lenient().when(fazendaRepository.listarIds()).thenReturn(List.of(1L));
        lenient().when(sensorRepository.listarPorFazenda(1L)).thenReturn(List.of(solo));
        lenient().when(avaliador.avaliarFazenda(1L)).thenReturn(Map.of(Acao.CRIADO, 1));
        lenient().when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.empty());
    }

    private void comEstacao() {
        Dispositivo d = new Dispositivo("EST", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        estacao = new EstacaoMeteorologica(d, "m", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 50L);
        temperaturaEstacao = new Sensor(new Dispositivo("EST-T", "SENSOR", null, StatusDispositivo.ATIVO, fazenda),
                VariavelMonitorada.TEMPERATURA_AR, estacao);
        ReflectionTestUtils.setField(temperaturaEstacao, "id", 51L);
        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.of(estacao));
        when(sensorRepository.listarDaEstacao(50L)).thenReturn(List.of(temperaturaEstacao));
    }

    @Test
    void entrarEmSecaReescreveOSoloSimuladoDaJanelaEAvaliaOsAlertas() {
        when(leituraRepository.sobrescreverValores(eq(List.of(20L)), eq(OrigemLeitura.SIMULADA),
                eq(AGORA.minusHours(6)), eq(GeradorLeituraSimulada.UMIDADE_SOLO_EM_SECA))).thenReturn(24);

        CenarioSimulacaoResponse resposta = service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.SECA, 1L));

        assertEquals(CenarioSimulacao.SECA, resposta.cenario());
        assertEquals(24, resposta.leiturasAjustadas());
        assertEquals(1, resposta.alertas().criados());
        assertEquals(CenarioSimulacao.SECA, estado.de(1L));
        assertEquals(CenarioSimulacao.NORMAL, estado.padrao());
        verify(avaliador).avaliarFazenda(1L);
    }

    @Test
    void sairDaSecaRestauraOSoloNormal() {
        estado.definir(1L, CenarioSimulacao.SECA);
        service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.NORMAL, 1L));
        verify(leituraRepository).sobrescreverValores(eq(List.of(20L)), eq(OrigemLeitura.SIMULADA), any(),
                eq(GeradorLeituraSimulada.UMIDADE_SOLO_NORMAL));
    }

    @Test
    void repetirOMesmoCenarioNaoReescreveLeituras() {
        estado.definir(1L, CenarioSimulacao.SECA);
        service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.SECA, 1L));
        verify(leituraRepository, never()).sobrescreverValores(any(), any(), any(), anyDouble());
    }

    @Test
    void soloInativoOuDeOutroTipoNaoEReescrito() {
        ReflectionTestUtils.setField(solo.getDispositivo(), "status", StatusDispositivo.INATIVO);
        CenarioSimulacaoResponse resposta = service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.SECA, 1L));
        assertEquals(0, resposta.leiturasAjustadas());
        verify(leituraRepository, never()).sobrescreverValores(any(), any(), any(), anyDouble());
    }

    @Test
    void geadaGravaTemperaturaBaixaNaEstacaoEDepoisUmaNormal() {
        comEstacao();

        service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.GEADA, 1L));
        verify(leituraService).registrar(temperaturaEstacao, GeradorLeituraSimulada.TEMPERATURA_EM_GEADA, AGORA,
                OrigemLeitura.SIMULADA);

        service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.NORMAL, 1L));
        verify(leituraService).registrar(temperaturaEstacao, GeradorLeituraSimulada.TEMPERATURA_NORMAL, AGORA,
                OrigemLeitura.SIMULADA);
    }

    @Test
    void geadaSemEstacaoAvisaENaoFalha() {
        CenarioSimulacaoResponse resposta = service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.GEADA, 1L));
        assertEquals(1, resposta.avisos().size());
        assertTrue(resposta.avisos().get(0).contains("não tem estação"));
        verifyNoInteractions(leituraService);
    }

    @Test
    void leituraJaExistenteNoMesmoSegundoViraAviso() {
        comEstacao();
        when(leituraService.registrar(any(), anyDouble(), any(), any())).thenThrow(new ConflitoException("dup"));
        CenarioSimulacaoResponse resposta = service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.GEADA, 1L));
        assertEquals(1, resposta.avisos().size());
    }

    @Test
    void falhaDeSensorSoMudaOEstado() {
        CenarioSimulacaoResponse resposta = service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.FALHA_SENSOR, 1L));
        assertEquals(CenarioSimulacao.FALHA_SENSOR, estado.de(1L));
        assertEquals(0, resposta.leiturasAjustadas());
        verify(leituraRepository, never()).sobrescreverValores(any(), any(), any(), anyDouble());
    }

    @Test
    void semFazendaIdVaiParaTodasEDescartaAsExcecoes() {
        estado.definir(1L, CenarioSimulacao.GEADA);
        CenarioSimulacaoResponse resposta = service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.NORMAL, null));
        assertEquals(List.of(1L), resposta.fazendas());
        assertEquals(CenarioSimulacao.NORMAL, estado.padrao());
        assertTrue(estado.excecoes().isEmpty());
    }

    @Test
    void fazendaInexistenteRetorna404SemMudarOEstado() {
        when(fazendaRepository.existsById(9L)).thenReturn(false);
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.SECA, 9L)));
        assertEquals(CenarioSimulacao.NORMAL, estado.padrao());
        assertTrue(estado.excecoes().isEmpty());
    }

    @Test
    void falhaNaAvaliacaoViraAvisoENaoDesfazOCenario() {
        when(avaliador.avaliarFazenda(1L)).thenThrow(new IllegalStateException("x"));
        CenarioSimulacaoResponse resposta = service.aplicar(new CenarioSimulacaoRequest(CenarioSimulacao.FALHA_SENSOR, 1L));
        assertEquals(CenarioSimulacao.FALHA_SENSOR, estado.de(1L));
        assertEquals(1, resposta.avisos().size());
        assertEquals(0, resposta.alertas().criados());
    }

    @Test
    void configuracaoInvalidaEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> new CenarioSimulacaoService(estado, fazendaRepository,
                sensorRepository, estacaoRepository, leituraRepository, leituraService, avaliador, 0, Clock.systemUTC()));
    }
}
