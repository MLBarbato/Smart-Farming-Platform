package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstacaoCadastroTest {
    @Mock private FazendaRepository fazendaRepository;
    @Mock private EstacaoMeteorologicaRepository estacaoRepository;
    @Mock private DispositivoRepository dispositivoRepository;
    @Mock private SensorRepository sensorRepository;
    @Mock private LeituraRepository leituraRepository;

    private EstacaoCadastro cadastro;
    private Fazenda fazenda;

    @BeforeEach
    void preparar() {
        cadastro = new EstacaoCadastro(fazendaRepository, estacaoRepository, dispositivoRepository, sensorRepository,
                leituraRepository, Clock.fixed(java.time.Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC));
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("Santa Maria", null, 100.0, -17.79, -50.92, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        lenient().when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
    }

    @Test
    void criaEstacaoVirtualComUmSensorPorVariavelDoProvedor() {
        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.empty());
        when(dispositivoRepository.existsByCodigoIdentificacao(any())).thenReturn(false);
        when(dispositivoRepository.save(any(Dispositivo.class))).thenAnswer(i -> i.getArgument(0));
        when(estacaoRepository.save(any(EstacaoMeteorologica.class))).thenAnswer(i -> i.getArgument(0));

        cadastro.criar(1L);

        ArgumentCaptor<EstacaoMeteorologica> estacao = ArgumentCaptor.forClass(EstacaoMeteorologica.class);
        verify(estacaoRepository).save(estacao.capture());
        assertEquals(FonteEstacao.OPEN_METEO, estacao.getValue().getFonte());
        assertEquals(60, estacao.getValue().getFrequenciaColeta());
        assertEquals("EST-1", estacao.getValue().getDispositivo().getCodigoIdentificacao());
        assertEquals("ESTACAO", estacao.getValue().getDispositivo().getTipoDispositivo());
        assertNull(estacao.getValue().getDispositivo().getTalhao());
        assertEquals(fazenda, estacao.getValue().getDispositivo().getFazenda());
        assertEquals(LocalDate.of(2026, 11, 16), estacao.getValue().getDispositivo().getDataInstalacao());

        ArgumentCaptor<Sensor> sensores = ArgumentCaptor.forClass(Sensor.class);
        verify(sensorRepository, times(5)).save(sensores.capture());
        assertEquals(List.of(VariavelMonitorada.TEMPERATURA_AR, VariavelMonitorada.UMIDADE_AR,
                        VariavelMonitorada.VELOCIDADE_VENTO, VariavelMonitorada.PRECIPITACAO, VariavelMonitorada.UMIDADE_SOLO),
                sensores.getAllValues().stream().map(Sensor::getTipoSensor).toList());
        assertTrue(sensores.getAllValues().stream().allMatch(s -> s.getEstacao() == estacao.getValue()));
        assertTrue(sensores.getAllValues().stream().allMatch(s -> s.getDispositivo().getTalhao() == null));
    }

    @Test
    void naoCriaSemCoordenadasNemEstacaoDuplicadaNemFazendaInexistente() {
        Fazenda semCoord = new Fazenda("X", null, 10.0, null, null, null, null, fazenda.getUsuario());
        ReflectionTestUtils.setField(semCoord, "id", 2L);
        when(fazendaRepository.findById(2L)).thenReturn(Optional.of(semCoord));
        assertThrows(RegraDeNegocioException.class, () -> cadastro.criar(2L));

        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.of(mock(EstacaoMeteorologica.class)));
        assertThrows(ConflitoException.class, () -> cadastro.criar(1L));

        when(fazendaRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> cadastro.criar(9L));
        verify(dispositivoRepository, never()).save(any());
    }

    @Test
    void rejeitaCodigoJaUsadoPorOutroDispositivo() {
        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.empty());
        when(dispositivoRepository.existsByCodigoIdentificacao("EST-1")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> cadastro.criar(1L));
        verify(dispositivoRepository, never()).save(any());
    }

    @Test
    void removeLeiturasSensoresEstacaoEDispositivosNessaOrdem() {
        Dispositivo dEstacao = new Dispositivo("EST-1", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        ReflectionTestUtils.setField(dEstacao, "id", 50L);
        EstacaoMeteorologica estacao = new EstacaoMeteorologica(dEstacao, "m", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 50L);
        Dispositivo dSensor = new Dispositivo("EST-1-T", "SENSOR", null, StatusDispositivo.ATIVO, fazenda);
        ReflectionTestUtils.setField(dSensor, "id", 51L);
        Sensor sensor = new Sensor(dSensor, VariavelMonitorada.TEMPERATURA_AR, estacao);
        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.of(estacao));
        when(sensorRepository.listarDaEstacao(50L)).thenReturn(List.of(sensor));

        cadastro.removerSeExistir(1L);

        InOrder ordem = inOrder(leituraRepository, sensorRepository, estacaoRepository, dispositivoRepository);
        ordem.verify(leituraRepository).deleteAllByDispositivo_IdIn(List.of(51L, 50L));
        ordem.verify(sensorRepository).deleteAll(List.of(sensor));
        ordem.verify(estacaoRepository).delete(estacao);
        ordem.verify(dispositivoRepository).deleteAll(List.of(dSensor, dEstacao));
    }

    @Test
    void removerSeExistirNaoFazNadaSemEstacaoEremoverFalhaComNaoEncontrado() {
        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.empty());

        cadastro.removerSeExistir(1L);
        verifyNoInteractions(leituraRepository, sensorRepository, dispositivoRepository);

        assertThrows(RecursoNaoEncontradoException.class, () -> cadastro.remover(1L));
    }

    @Test
    void alterarStatusAtualizaODispositivoDaEstacao() {
        Dispositivo d = new Dispositivo("EST-1", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        when(estacaoRepository.buscarDaFazenda(1L))
                .thenReturn(Optional.of(new EstacaoMeteorologica(d, "m", 60, FonteEstacao.OPEN_METEO)));

        cadastro.alterarStatus(1L, StatusDispositivo.INATIVO);

        assertEquals(StatusDispositivo.INATIVO, d.getStatus());
    }
}
