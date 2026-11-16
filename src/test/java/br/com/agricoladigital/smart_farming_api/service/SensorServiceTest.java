package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusSensorRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.SensorRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.SensorResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {
    @Mock private SensorRepository sensorRepository;
    @Mock private DispositivoRepository dispositivoRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private TalhaoRepository talhaoRepository;
    @InjectMocks private SensorService sensorService;

    private Talhao talhao;

    @BeforeEach
    void preparar() {
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        Fazenda fazenda = new Fazenda("Horizonte", "GO", 100.0, null, null, null, null, dono);
        talhao = new Talhao("T1", 10.0, null, fazenda);
        ReflectionTestUtils.setField(talhao, "id", 5L);
    }

    private void talhaoExiste() {
        when(talhaoRepository.findByIdAndFazenda_Id(5L, 1L)).thenReturn(Optional.of(talhao));
    }

    private Sensor sensor(VariavelMonitorada variavel, StatusDispositivo status) {
        Dispositivo dispositivo = new Dispositivo("S-" + variavel, "SENSOR", null, status, talhao);
        Sensor sensor = new Sensor(dispositivo, variavel);
        ReflectionTestUtils.setField(sensor, "id", 9L);
        return sensor;
    }

    @Test
    void criarGravaDispositivoAtivoESensorComUnidadeDaVariavel() {
        talhaoExiste();
        when(dispositivoRepository.existsByCodigoIdentificacao("SOLO-01")).thenReturn(false);
        when(dispositivoRepository.save(any(Dispositivo.class))).thenAnswer(i -> i.getArgument(0));
        when(sensorRepository.save(any(Sensor.class))).thenAnswer(i -> i.getArgument(0));

        SensorResponse response = sensorService.criar(1L, 5L,
                new SensorRequest("  SOLO-01 ", VariavelMonitorada.UMIDADE_SOLO, null));

        ArgumentCaptor<Dispositivo> captor = ArgumentCaptor.forClass(Dispositivo.class);
        verify(dispositivoRepository).save(captor.capture());
        assertEquals("SOLO-01", captor.getValue().getCodigoIdentificacao());
        assertEquals("SENSOR", captor.getValue().getTipoDispositivo());
        assertEquals(StatusDispositivo.ATIVO, response.status());
        assertEquals("%", response.unidade());
        assertEquals(VariavelMonitorada.UMIDADE_SOLO, response.variavel());
        assertEquals(5L, response.talhaoId());
    }

    @Test
    void criarRejeitaCodigoDuplicado() {
        talhaoExiste();
        when(dispositivoRepository.existsByCodigoIdentificacao("SOLO-01")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> sensorService.criar(1L, 5L,
                new SensorRequest("SOLO-01", VariavelMonitorada.UMIDADE_SOLO, null)));
        verify(sensorRepository, never()).save(any());
    }

    @Test
    void criarRejeitaTalhaoQueNaoPertenceAFazenda() {
        when(talhaoRepository.findByIdAndFazenda_Id(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> sensorService.criar(1L, 5L,
                new SensorRequest("X", VariavelMonitorada.PH_SOLO, null)));
    }

    @Test
    void listarFiltraPorVariavelEStatus() {
        talhaoExiste();
        when(sensorRepository.listarPorTalhao(5L)).thenReturn(List.of(
                sensor(VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO),
                sensor(VariavelMonitorada.UMIDADE_SOLO, StatusDispositivo.ATIVO),
                sensor(VariavelMonitorada.PH_SOLO, StatusDispositivo.INATIVO)));

        assertEquals(3, sensorService.listar(1L, 5L, null, null).size());
        assertEquals(2, sensorService.listar(1L, 5L, VariavelMonitorada.PH_SOLO, null).size());
        assertEquals(1, sensorService.listar(1L, 5L, VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO).size());
    }

    @Test
    void alterarStatusAtualizaODispositivo() {
        talhaoExiste();
        Sensor sensor = sensor(VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO);
        when(sensorRepository.buscarNoTalhao(9L, 5L)).thenReturn(Optional.of(sensor));

        SensorResponse response = sensorService.alterarStatus(1L, 5L, 9L,
                new AlterarStatusSensorRequest(StatusDispositivo.MANUTENCAO));

        assertEquals(StatusDispositivo.MANUTENCAO, response.status());
    }

    @Test
    void excluirRejeitaSensorComLeituras() {
        talhaoExiste();
        when(sensorRepository.buscarNoTalhao(9L, 5L))
                .thenReturn(Optional.of(sensor(VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO)));
        when(leituraRepository.existsByDispositivo_Id(9L)).thenReturn(true);

        assertThrows(ConflitoException.class, () -> sensorService.excluir(1L, 5L, 9L));
        verify(sensorRepository, never()).delete(any());
    }

    @Test
    void excluirRemoveSensorEDispositivoSemLeituras() {
        talhaoExiste();
        Sensor sensor = sensor(VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO);
        when(sensorRepository.buscarNoTalhao(9L, 5L)).thenReturn(Optional.of(sensor));
        when(leituraRepository.existsByDispositivo_Id(9L)).thenReturn(false);

        sensorService.excluir(1L, 5L, 9L);

        verify(sensorRepository).delete(sensor);
        verify(dispositivoRepository).delete(sensor.getDispositivo());
    }

    @Test
    void sensorDeOutroTalhaoRetornaNaoEncontrado() {
        talhaoExiste();
        when(sensorRepository.buscarNoTalhao(9L, 5L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> sensorService.buscar(1L, 5L, 9L));
    }
}
