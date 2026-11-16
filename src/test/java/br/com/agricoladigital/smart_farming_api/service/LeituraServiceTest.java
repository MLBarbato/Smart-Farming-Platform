package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.SensorLeituraAtualResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeituraServiceTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 6, 15, 12, 0, 0);

    @Mock private LeituraRepository leituraRepository;
    @Mock private SensorRepository sensorRepository;
    @Mock private SensorService sensorService;

    private LeituraService leituraService;
    private Talhao talhao;

    @BeforeEach
    void preparar() {
        Clock relogio = Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        leituraService = new LeituraService(leituraRepository, sensorRepository, sensorService, relogio);
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        talhao = new Talhao("T1", 10.0, null, new Fazenda("H", "GO", 100.0, null, null, null, null, dono));
        ReflectionTestUtils.setField(talhao, "id", 5L);
    }

    private Sensor sensor(VariavelMonitorada variavel, StatusDispositivo status) {
        Sensor sensor = new Sensor(new Dispositivo("S-1", "SENSOR", null, status, talhao), variavel);
        ReflectionTestUtils.setField(sensor, "id", 9L);
        return sensor;
    }

    private static Leitura leitura(Sensor sensor, double valor, LocalDateTime dataHora) {
        return new Leitura(sensor.getDispositivo(), valor, dataHora, OrigemLeitura.SIMULADA);
    }

    @Test
    void registrarGravaLeituraValida() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(leituraRepository.existsByDispositivo_IdAndDataHora(9L, AGORA)).thenReturn(false);
        when(leituraRepository.save(any(Leitura.class))).thenAnswer(i -> i.getArgument(0));

        LeituraResponse response = leituraService.registrar(sensor, 25.5, AGORA, OrigemLeitura.REAL);

        assertEquals(25.5, response.valor());
        assertEquals("°C", response.unidade());
        assertEquals(OrigemLeitura.REAL, response.origem());
        assertEquals(ZoneOffset.UTC, response.dataHora().getOffset());
        assertEquals(AGORA, response.dataHora().toLocalDateTime());
    }

    @Test
    void registrarRejeitaSensorInativo() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.MANUTENCAO);

        assertThrows(RegraDeNegocioException.class,
                () -> leituraService.registrar(sensor, 25.0, AGORA, OrigemLeitura.REAL));
        verify(leituraRepository, never()).save(any());
    }

    @Test
    void registrarRejeitaValorForaDaFaixaOuNaoNumerico() {
        Sensor ph = sensor(VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO);

        assertThrows(RegraDeNegocioException.class, () -> leituraService.registrar(ph, 14.0, AGORA, OrigemLeitura.REAL));
        assertThrows(RegraDeNegocioException.class, () -> leituraService.registrar(ph, -1.0, AGORA, OrigemLeitura.REAL));
        assertThrows(RegraDeNegocioException.class, () -> leituraService.registrar(ph, Double.NaN, AGORA, OrigemLeitura.REAL));
        assertThrows(RegraDeNegocioException.class, () -> leituraService.registrar(ph, null, AGORA, OrigemLeitura.REAL));
        verify(leituraRepository, never()).save(any());
    }

    @Test
    void registrarRejeitaInstanteNoFuturoMasToleraPequenaDiferenca() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(leituraRepository.save(any(Leitura.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(RegraDeNegocioException.class,
                () -> leituraService.registrar(sensor, 25.0, AGORA.plusMinutes(6), OrigemLeitura.REAL));
        assertDoesNotThrow(() -> leituraService.registrar(sensor, 25.0, AGORA.plusMinutes(4), OrigemLeitura.REAL));
    }

    @Test
    void registrarRejeitaInstanteDuplicado() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(leituraRepository.existsByDispositivo_IdAndDataHora(9L, AGORA)).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> leituraService.registrar(sensor, 25.0, AGORA, OrigemLeitura.REAL));
        verify(leituraRepository, never()).save(any());
    }

    @Test
    void historicoPadraoCobreAsUltimas24HorasEDevolveEmOrdemCronologica() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(sensorService.localizar(1L, 5L, 9L)).thenReturn(sensor);
        when(leituraRepository.findByDispositivo_IdAndDataHoraBetweenOrderByDataHoraDesc(
                eq(9L), eq(AGORA.minusHours(24)), eq(AGORA), any(Pageable.class)))
                .thenReturn(List.of(leitura(sensor, 3, AGORA), leitura(sensor, 2, AGORA.minusHours(1)),
                        leitura(sensor, 1, AGORA.minusHours(2))));

        List<LeituraResponse> resposta = leituraService.historico(1L, 5L, 9L, null, null, null);

        assertEquals(List.of(1.0, 2.0, 3.0), resposta.stream().map(LeituraResponse::valor).toList());
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(leituraRepository).findByDispositivo_IdAndDataHoraBetweenOrderByDataHoraDesc(
                eq(9L), any(), any(), pagina.capture());
        assertEquals(LeituraService.LIMITE_PADRAO, pagina.getValue().getPageSize());
    }

    @Test
    void historicoConverteFusoParaUtc() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(sensorService.localizar(1L, 5L, 9L)).thenReturn(sensor);
        OffsetDateTime de = OffsetDateTime.parse("2026-06-15T08:00:00-03:00"); // 11:00 UTC

        leituraService.historico(1L, 5L, 9L, de, null, 10);

        verify(leituraRepository).findByDispositivo_IdAndDataHoraBetweenOrderByDataHoraDesc(
                eq(9L), eq(LocalDateTime.of(2026, 6, 15, 11, 0)), eq(AGORA), any(Pageable.class));
    }

    @Test
    void historicoRejeitaIntervaloInvertidoELimiteInvalido() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(sensorService.localizar(1L, 5L, 9L)).thenReturn(sensor);
        OffsetDateTime de = AGORA.atOffset(ZoneOffset.UTC);

        assertThrows(RegraDeNegocioException.class,
                () -> leituraService.historico(1L, 5L, 9L, de, de.minusHours(1), null));
        assertThrows(RegraDeNegocioException.class, () -> leituraService.historico(1L, 5L, 9L, null, null, 0));
        assertThrows(RegraDeNegocioException.class,
                () -> leituraService.historico(1L, 5L, 9L, null, null, LeituraService.LIMITE_MAXIMO + 1));
    }

    @Test
    void ultimaRetornaNaoEncontradoQuandoNaoHaLeituras() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(sensorService.localizar(1L, 5L, 9L)).thenReturn(sensor);
        when(leituraRepository.findFirstByDispositivo_IdOrderByDataHoraDesc(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> leituraService.ultima(1L, 5L, 9L));
    }

    @Test
    void atuaisTrazUltimaLeituraOuNuloParaSensorSemLeitura() {
        Sensor comLeitura = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        Sensor semLeitura = sensor(VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO);
        ReflectionTestUtils.setField(semLeitura, "id", 10L);
        when(sensorRepository.listarPorTalhao(5L)).thenReturn(List.of(comLeitura, semLeitura));
        when(leituraRepository.findFirstByDispositivo_IdOrderByDataHoraDesc(9L))
                .thenReturn(Optional.of(leitura(comLeitura, 22.0, AGORA)));
        when(leituraRepository.findFirstByDispositivo_IdOrderByDataHoraDesc(10L)).thenReturn(Optional.empty());

        List<SensorLeituraAtualResponse> atuais = leituraService.atuais(1L, 5L);

        assertEquals(22.0, atuais.get(0).ultimaLeitura().valor());
        assertNull(atuais.get(1).ultimaLeitura());
    }

    @Test
    void registrarLoteGravaTodasAsMedicoesValidas() {
        Sensor sensor = sensor(VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        when(leituraRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        int gravadas = leituraService.registrarLote(sensor, List.of(
                new LeituraService.Medicao(AGORA.minusHours(2), 20.0),
                new LeituraService.Medicao(AGORA.minusHours(1), 21.0)), OrigemLeitura.SIMULADA);

        assertEquals(2, gravadas);
    }

    @Test
    void registrarLoteNaoGravaNadaSeUmaMedicaoForInvalida() {
        Sensor sensor = sensor(VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO);

        assertThrows(RegraDeNegocioException.class, () -> leituraService.registrarLote(sensor, List.of(
                new LeituraService.Medicao(AGORA.minusHours(2), 6.0),
                new LeituraService.Medicao(AGORA.minusHours(1), 99.0)), OrigemLeitura.SIMULADA));
        verify(leituraRepository, never()).saveAll(any());
    }

    @Test
    void temLeiturasConsultaORepositorio() {
        when(leituraRepository.existsByDispositivo_Id(9L)).thenReturn(true);

        assertTrue(leituraService.temLeituras(9L));
    }
}
