package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.EstacaoResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstacaoServiceTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);

    @Mock private EstacaoCadastro cadastro;
    @Mock private EstacaoSincronizador sincronizador;
    @Mock private SensorRepository sensorRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private LeituraService leituraService;

    private EstacaoService servico;
    private EstacaoMeteorologica estacao;
    private Fazenda fazenda;
    private Sensor temperatura;

    @BeforeEach
    void preparar() {
        servico = new EstacaoService(cadastro, sincronizador, sensorRepository, leituraRepository, leituraService, 5,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("Santa Maria", null, 100.0, -17.79, -50.92, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        Dispositivo d = new Dispositivo("EST-1", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        estacao = new EstacaoMeteorologica(d, "Open-Meteo", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 50L);
        temperatura = new Sensor(new Dispositivo("EST-1-T", "SENSOR", null, StatusDispositivo.ATIVO, fazenda),
                VariavelMonitorada.TEMPERATURA_AR, estacao);
        ReflectionTestUtils.setField(temperatura, "id", 51L);
        ReflectionTestUtils.setField(temperatura.getDispositivo(), "id", 51L);
        lenient().when(cadastro.encontrar(1L)).thenReturn(estacao);
        lenient().when(sensorRepository.listarDaEstacao(50L)).thenReturn(List.of(temperatura));
        lenient().when(leituraRepository.ultimasPorDispositivos(any())).thenReturn(List.of());
    }

    @Test
    void consultaTrazDadosDaEstacaoEUltimasLeituras() {
        estacao.registrarSincronizacao(AGORA.minusMinutes(30));
        when(leituraRepository.ultimasPorDispositivos(any())).thenReturn(List.of(
                new Leitura(temperatura.getDispositivo(), 24.6, AGORA.minusHours(1), OrigemLeitura.EXTERNA)));

        EstacaoResponse r = servico.consultar(1L);

        assertEquals("EST-1", r.codigo());
        assertEquals(FonteEstacao.OPEN_METEO, r.fonte());
        assertEquals(-17.79, r.latitude());
        assertEquals(SituacaoEstacao.ATUAL, r.situacao());
        assertEquals(AGORA.minusMinutes(30), r.ultimaSincronizacao().toLocalDateTime());
        assertNotNull(r.atribuicao());
        assertEquals(1, r.leiturasAtuais().size());
        assertEquals(24.6, r.leiturasAtuais().get(0).valor());
        assertEquals(OrigemLeitura.EXTERNA, r.leiturasAtuais().get(0).origem());
    }

    @Test
    void situacaoDependeDaIdadeDaSincronizacaoEDoStatus() {
        assertEquals(SituacaoEstacao.SEM_SINCRONIZACAO, servico.consultar(1L).situacao());

        estacao.registrarSincronizacao(AGORA.minusMinutes(120));
        assertEquals(SituacaoEstacao.ATUAL, servico.consultar(1L).situacao());   // exatamente 2x a frequência

        estacao.registrarSincronizacao(AGORA.minusMinutes(121));
        assertEquals(SituacaoEstacao.DESATUALIZADA, servico.consultar(1L).situacao());

        estacao.getDispositivo().alterarStatus(StatusDispositivo.INATIVO);
        assertEquals(SituacaoEstacao.INATIVA, servico.consultar(1L).situacao());
    }

    @Test
    void criarSincronizaDeImediatoMasToleraFalhaDoProvedor() {
        doThrow(new PrevisaoIndisponivelException("fora do ar")).when(sincronizador).sincronizar(estacao);

        EstacaoResponse r = servico.criar(1L);

        verify(cadastro).criar(1L);
        assertEquals(SituacaoEstacao.SEM_SINCRONIZACAO, r.situacao());
    }

    @Test
    void sincronizacaoManualRecenteNaoChamaOProvedor() {
        estacao.registrarSincronizacao(AGORA.minusMinutes(4));

        servico.sincronizarManualmente(1L);

        verifyNoInteractions(sincronizador);
    }

    @Test
    void sincronizacaoManualAposOIntervaloOuSemSincronizacaoChamaOProvedor() {
        servico.sincronizarManualmente(1L);
        estacao.registrarSincronizacao(AGORA.minusMinutes(5));
        servico.sincronizarManualmente(1L);

        verify(sincronizador, times(2)).sincronizar(estacao);
    }

    @Test
    void sincronizacaoManualDeEstacaoInativaRetorna400MesmoComSincronizacaoRecente() {
        estacao.registrarSincronizacao(AGORA.minusMinutes(1));
        estacao.getDispositivo().alterarStatus(StatusDispositivo.INATIVO);

        assertThrows(RegraDeNegocioException.class, () -> servico.sincronizarManualmente(1L));
        verifyNoInteractions(sincronizador);
    }

    @Test
    void falhaDoProvedorNaSincronizacaoManualPropaga() {
        doThrow(new PrevisaoIndisponivelException("fora do ar")).when(sincronizador).sincronizar(estacao);

        assertThrows(PrevisaoIndisponivelException.class, () -> servico.sincronizarManualmente(1L));
    }

    @Test
    void historicoUsaOSensorDaVariavelPedida() {
        List<LeituraResponse> esperado = List.of();
        when(leituraService.historicoDoSensor(temperatura, null, null, 10)).thenReturn(esperado);

        assertSame(esperado, servico.historico(1L, VariavelMonitorada.TEMPERATURA_AR, null, null, 10));
    }

    @Test
    void historicoDeVariavelQueAEstacaoNaoMedeRetorna400() {
        assertThrows(RegraDeNegocioException.class,
                () -> servico.historico(1L, VariavelMonitorada.PH_SOLO, null, null, null));
        verifyNoInteractions(leituraService);
    }

    @Test
    void alterarStatusDelegaEDevolveOEstadoAtual() {
        EstacaoResponse r = servico.alterarStatus(1L, StatusDispositivo.INATIVO);

        verify(cadastro).alterarStatus(1L, StatusDispositivo.INATIVO);
        assertNotNull(r);
    }

    @Test
    void removerDelegaAoCadastro() {
        servico.remover(1L);

        verify(cadastro).remover(1L);
    }
}
