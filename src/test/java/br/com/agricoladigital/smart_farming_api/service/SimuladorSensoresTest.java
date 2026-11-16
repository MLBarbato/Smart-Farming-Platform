package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimuladorSensoresTest {
    @Mock private SensorRepository sensorRepository;
    @Mock private LeituraService leituraService;
    @Mock private EstacaoMeteorologicaRepository estacaoRepository;
    private final EstadoSimulacao estado = new EstadoSimulacao();

    private Sensor sensor;
    private Fazenda fazenda;

    @BeforeEach
    void preparar() {
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("H", "GO", 100.0, null, null, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        Talhao talhao = new Talhao("T1", 10.0, null, fazenda);
        sensor = new Sensor(new Dispositivo("S-1", "SENSOR", null, StatusDispositivo.ATIVO, talhao),
                VariavelMonitorada.TEMPERATURA_AR);
        ReflectionTestUtils.setField(sensor, "id", 9L);
        lenient().when(sensorRepository.listarPorStatus(StatusDispositivo.ATIVO)).thenReturn(List.of(sensor));
        lenient().when(estacaoRepository.listarPorStatus(StatusDispositivo.ATIVO)).thenReturn(List.of());
    }

    @Test
    void sensorSemLeiturasRecebeHistoricoEDepoisALeituraAtual() {
        when(leituraService.temLeituras(9L)).thenReturn(false);
        SimuladorSensores simulador = new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60);

        simulador.gerarLeituras();

        ArgumentCaptor<List<LeituraService.Medicao>> lote = ArgumentCaptor.forClass(List.class);
        verify(leituraService).registrarLote(eq(sensor), lote.capture(), eq(OrigemLeitura.SIMULADA));
        assertEquals(24, lote.getValue().size());
        verify(leituraService).registrar(eq(sensor), anyDouble(), any(), eq(OrigemLeitura.SIMULADA));
    }

    @Test
    void sensorQueJaTemLeiturasNaoRecebeHistorico() {
        when(leituraService.temLeituras(9L)).thenReturn(true);
        SimuladorSensores simulador = new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60);

        simulador.gerarLeituras();

        verify(leituraService, never()).registrarLote(any(), any(), any());
        verify(leituraService).registrar(eq(sensor), anyDouble(), any(), eq(OrigemLeitura.SIMULADA));
    }

    @Test
    void historicoDesligadoComZeroHoras() {
        SimuladorSensores simulador = new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 0, 15);

        simulador.gerarLeituras();

        verify(leituraService, never()).temLeituras(anyLong());
        verify(leituraService, never()).registrarLote(any(), any(), any());
        verify(leituraService).registrar(eq(sensor), anyDouble(), any(), eq(OrigemLeitura.SIMULADA));
    }

    @Test
    void falhaNoHistoricoNaoImpedeOsDemaisSensores() {
        Sensor outro = new Sensor(sensor.getDispositivo(), VariavelMonitorada.PH_SOLO);
        ReflectionTestUtils.setField(outro, "id", 10L);
        when(sensorRepository.listarPorStatus(StatusDispositivo.ATIVO)).thenReturn(List.of(sensor, outro));
        when(leituraService.temLeituras(9L)).thenThrow(new IllegalStateException("falha"));
        when(leituraService.temLeituras(10L)).thenReturn(true);
        SimuladorSensores simulador = new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60);

        assertDoesNotThrow(simulador::gerarLeituras);

        verify(leituraService).registrar(eq(outro), anyDouble(), any(), eq(OrigemLeitura.SIMULADA));
    }

    @Test
    void configuracaoInvalidaEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, -1, 15));
        assertThrows(IllegalArgumentException.class, () -> new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, SimuladorSensores.MAXIMO_HORAS_DE_HISTORICO + 1, 15));
    }

    private Sensor soloDoTalhao() {
        Sensor solo = new Sensor(sensor.getDispositivo(), VariavelMonitorada.UMIDADE_SOLO);
        ReflectionTestUtils.setField(solo, "id", 11L);
        when(sensorRepository.listarPorStatus(StatusDispositivo.ATIVO)).thenReturn(List.of(solo));
        return solo;
    }

    @Test
    void cenarioSecaFixaAUmidadeDoSoloBaixaSoNasFazendasAfetadas() {
        Sensor solo = soloDoTalhao();
        when(leituraService.temLeituras(11L)).thenReturn(true);
        estado.definir(1L, CenarioSimulacao.SECA);

        new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60).gerarLeituras();
        verify(leituraService).registrar(eq(solo), eq(GeradorLeituraSimulada.UMIDADE_SOLO_EM_SECA), any(),
                eq(OrigemLeitura.SIMULADA));

        estado.definir(1L, CenarioSimulacao.NORMAL);
        new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60).gerarLeituras();
        verify(leituraService, times(1)).registrar(eq(solo), eq(GeradorLeituraSimulada.UMIDADE_SOLO_EM_SECA), any(), any());
        verify(leituraService, times(2)).registrar(eq(solo), anyDouble(), any(), eq(OrigemLeitura.SIMULADA));
    }

    @Test
    void cenarioSecaNaoMudaOsOutrosSensores() {
        when(leituraService.temLeituras(9L)).thenReturn(true);
        estado.definir(null, CenarioSimulacao.SECA);

        new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60).gerarLeituras();

        ArgumentCaptor<Double> valor = ArgumentCaptor.forClass(Double.class);
        verify(leituraService).registrar(eq(sensor), valor.capture(), any(), eq(OrigemLeitura.SIMULADA));
        assertNotEquals(GeradorLeituraSimulada.UMIDADE_SOLO_EM_SECA, valor.getValue());
    }

    @Test
    void cenarioFalhaDeSensorParaDeEnviarLeituras() {
        estado.definir(1L, CenarioSimulacao.FALHA_SENSOR);

        new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60).gerarLeituras();

        verify(leituraService, never()).registrar(any(), anyDouble(), any(), any());
        verify(leituraService, never()).registrarLote(any(), any(), any());
    }

    @Test
    void cenarioGeadaGravaTemperaturaBaixaNaEstacaoDaFazenda() {
        when(leituraService.temLeituras(9L)).thenReturn(true);
        Dispositivo dispositivoEstacao = new Dispositivo("EST", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        EstacaoMeteorologica estacao = new EstacaoMeteorologica(dispositivoEstacao, "m", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 50L);
        Sensor temperatura = new Sensor(new Dispositivo("EST-T", "SENSOR", null, StatusDispositivo.ATIVO, fazenda),
                VariavelMonitorada.TEMPERATURA_AR, estacao);
        ReflectionTestUtils.setField(temperatura, "id", 51L);
        Sensor umidade = new Sensor(new Dispositivo("EST-U", "SENSOR", null, StatusDispositivo.ATIVO, fazenda),
                VariavelMonitorada.UMIDADE_AR, estacao);
        ReflectionTestUtils.setField(umidade, "id", 52L);
        when(estacaoRepository.listarPorStatus(StatusDispositivo.ATIVO)).thenReturn(List.of(estacao));
        when(sensorRepository.listarDaEstacao(50L)).thenReturn(List.of(temperatura, umidade));

        new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60).gerarLeituras();
        verify(leituraService, never()).registrar(eq(temperatura), anyDouble(), any(), any());

        estado.definir(1L, CenarioSimulacao.GEADA);
        new SimuladorSensores(sensorRepository, leituraService, estacaoRepository, estado, 24, 60).gerarLeituras();
        verify(leituraService).registrar(eq(temperatura), eq(GeradorLeituraSimulada.TEMPERATURA_EM_GEADA), any(),
                eq(OrigemLeitura.SIMULADA));
        verify(leituraService, never()).registrar(eq(umidade), anyDouble(), any(), any());
    }
}
