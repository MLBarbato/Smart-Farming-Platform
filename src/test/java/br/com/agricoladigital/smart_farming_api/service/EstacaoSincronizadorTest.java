package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.client.ClimaClient;
import br.com.agricoladigital.smart_farming_api.client.MedicaoHoraria;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstacaoSincronizadorTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 58, 20);
    private static final LocalDateTime HORA_ATUAL = LocalDateTime.of(2026, 11, 16, 12, 0, 0);

    @Mock private ClimaClient clima;
    @Mock private SensorRepository sensorRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private LeituraService leituraService;
    @Mock private EstacaoCadastro cadastro;

    private EstacaoSincronizador sincronizador;
    private EstacaoMeteorologica estacao;
    private Fazenda fazenda;
    private Sensor temperatura;
    private Sensor chuva;

    @BeforeEach
    void preparar() {
        sincronizador = new EstacaoSincronizador(clima, sensorRepository, leituraRepository, leituraService, cadastro,
                72, 168, Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("Santa Maria", null, 100.0, -17.79, -50.92, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        Dispositivo dispositivo = new Dispositivo("EST-1", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        estacao = new EstacaoMeteorologica(dispositivo, "m", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 50L);
        temperatura = sensor(100, VariavelMonitorada.TEMPERATURA_AR);
        chuva = sensor(101, VariavelMonitorada.PRECIPITACAO);
        lenient().when(sensorRepository.listarDaEstacao(50L)).thenReturn(List.of(temperatura, chuva));
        lenient().when(leituraRepository.findFirstByDispositivo_IdOrderByDataHoraDesc(anyLong()))
                .thenReturn(Optional.empty());
    }

    private Sensor sensor(long id, VariavelMonitorada variavel) {
        Sensor sensor = new Sensor(new Dispositivo("EST-1-" + variavel, "SENSOR", null, StatusDispositivo.ATIVO, fazenda),
                variavel, estacao);
        ReflectionTestUtils.setField(sensor, "id", id);
        return sensor;
    }

    private void ultimaLeitura(Sensor sensor, LocalDateTime quando) {
        when(leituraRepository.findFirstByDispositivo_IdOrderByDataHoraDesc(sensor.getId()))
                .thenReturn(Optional.of(new Leitura(sensor.getDispositivo(), 1.0, quando, OrigemLeitura.EXTERNA)));
    }

    private static MedicaoHoraria hora(int h, Double temp, Double chuva) {
        return new MedicaoHoraria(HORA_ATUAL.withHour(h), temp, 70.0, 8.0, chuva, 44.0);
    }

    @Test
    void primeiraSincronizacaoPedeOHistoricoInicialEGravaCadaVariavelComOrigemExterna() {
        when(clima.buscarMedicoesHorarias(-17.79, -50.92, 72))
                .thenReturn(List.of(hora(10, 22.0, 0.0), hora(11, 23.0, 1.5), hora(12, 24.0, 0.0)));
        when(leituraService.registrarLote(any(), any(), any())).thenAnswer(i -> ((List<?>) i.getArgument(1)).size());

        int gravadas = sincronizador.sincronizar(estacao);

        assertEquals(6, gravadas);
        ArgumentCaptor<List<LeituraService.Medicao>> temp = ArgumentCaptor.forClass(List.class);
        verify(leituraService).registrarLote(eq(temperatura), temp.capture(), eq(OrigemLeitura.EXTERNA));
        assertEquals(List.of(22.0, 23.0, 24.0), temp.getValue().stream().map(LeituraService.Medicao::valor).toList());
        ArgumentCaptor<List<LeituraService.Medicao>> mm = ArgumentCaptor.forClass(List.class);
        verify(leituraService).registrarLote(eq(chuva), mm.capture(), eq(OrigemLeitura.EXTERNA));
        assertEquals(List.of(0.0, 1.5, 0.0), mm.getValue().stream().map(LeituraService.Medicao::valor).toList());
        verify(cadastro).registrarSincronizacao(50L, AGORA);
    }

    @Test
    void gravaSoHorasMaisNovasQueAUltimaLeituraDeCadaSensor() {
        ultimaLeitura(temperatura, HORA_ATUAL.minusHours(1));   // 11:00
        ultimaLeitura(chuva, HORA_ATUAL);                       // 12:00 (já em dia)
        when(clima.buscarMedicoesHorarias(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(hora(10, 22.0, 0.0), hora(11, 23.0, 1.5), hora(12, 24.0, 0.0)));
        when(leituraService.registrarLote(any(), any(), any())).thenAnswer(i -> ((List<?>) i.getArgument(1)).size());

        int gravadas = sincronizador.sincronizar(estacao);

        assertEquals(1, gravadas);
        ArgumentCaptor<List<LeituraService.Medicao>> temp = ArgumentCaptor.forClass(List.class);
        verify(leituraService).registrarLote(eq(temperatura), temp.capture(), any());
        assertEquals(List.of(24.0), temp.getValue().stream().map(LeituraService.Medicao::valor).toList());
        verify(leituraService, never()).registrarLote(eq(chuva), any(), any());
    }

    @Test
    void recuperaLacunaPedindoDesdeALeituraMaisAntiga() {
        ultimaLeitura(temperatura, HORA_ATUAL.minusHours(10));
        ultimaLeitura(chuva, HORA_ATUAL.minusHours(4));
        when(clima.buscarMedicoesHorarias(-17.79, -50.92, 11)).thenReturn(List.of());

        sincronizador.sincronizar(estacao);

        verify(clima).buscarMedicoesHorarias(-17.79, -50.92, 11);
    }

    @Test
    void janelaTemMinimoDeTresHorasETetoDeRecuperacao() {
        ultimaLeitura(temperatura, HORA_ATUAL);
        ultimaLeitura(chuva, HORA_ATUAL);
        when(clima.buscarMedicoesHorarias(anyDouble(), anyDouble(), eq(3))).thenReturn(List.of());
        sincronizador.sincronizar(estacao);
        verify(clima).buscarMedicoesHorarias(-17.79, -50.92, 3);

        reset(leituraRepository);
        ultimaLeitura(temperatura, HORA_ATUAL.minusDays(30));
        ultimaLeitura(chuva, HORA_ATUAL.minusDays(30));
        when(clima.buscarMedicoesHorarias(anyDouble(), anyDouble(), eq(168))).thenReturn(List.of());
        sincronizador.sincronizar(estacao);
        verify(clima).buscarMedicoesHorarias(-17.79, -50.92, 168);
    }

    @Test
    void ignoraHorasFuturasEValoresNulosDoProvedor() {
        when(clima.buscarMedicoesHorarias(anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(
                hora(12, 24.0, null), hora(13, 99.0, 9.0)));
        when(leituraService.registrarLote(any(), any(), any())).thenAnswer(i -> ((List<?>) i.getArgument(1)).size());

        sincronizador.sincronizar(estacao);

        ArgumentCaptor<List<LeituraService.Medicao>> temp = ArgumentCaptor.forClass(List.class);
        verify(leituraService).registrarLote(eq(temperatura), temp.capture(), any());
        assertEquals(List.of(24.0), temp.getValue().stream().map(LeituraService.Medicao::valor).toList());
        verify(leituraService, never()).registrarLote(eq(chuva), any(), any());   // chuva nula na hora 12
    }

    @Test
    void falhaEmUmSensorNaoMarcaSincronizacaoMasNaoImpedeOsDemais() {
        when(clima.buscarMedicoesHorarias(anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(hora(12, 24.0, 0.0)));
        when(leituraService.registrarLote(eq(temperatura), any(), any())).thenThrow(new IllegalStateException("duplicada"));
        when(leituraService.registrarLote(eq(chuva), any(), any())).thenReturn(1);

        int gravadas = sincronizador.sincronizar(estacao);

        assertEquals(1, gravadas);
        verify(cadastro, never()).registrarSincronizacao(anyLong(), any());
    }

    @Test
    void falhaDoProvedorPropagaSemGravarNada() {
        when(clima.buscarMedicoesHorarias(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new PrevisaoIndisponivelException("fora do ar"));

        assertThrows(PrevisaoIndisponivelException.class, () -> sincronizador.sincronizar(estacao));
        verifyNoInteractions(leituraService, cadastro);
    }

    @Test
    void fazendaSemCoordenadasOuEstacaoInativaNaoSincroniza() {
        Fazenda semCoord = new Fazenda("X", null, 10.0, null, null, null, null, fazenda.getUsuario());
        EstacaoMeteorologica semCoordenadas = new EstacaoMeteorologica(
                new Dispositivo("E", "ESTACAO", null, StatusDispositivo.ATIVO, semCoord), "m", 60, FonteEstacao.OPEN_METEO);
        assertThrows(RegraDeNegocioException.class, () -> sincronizador.sincronizar(semCoordenadas));

        estacao.getDispositivo().alterarStatus(StatusDispositivo.INATIVO);
        assertThrows(RegraDeNegocioException.class, () -> sincronizador.sincronizar(estacao));
        verifyNoInteractions(clima);
    }

    @Test
    void configuracaoInvalidaEhRejeitada() {
        Clock relogio = Clock.systemUTC();
        assertThrows(IllegalArgumentException.class, () -> new EstacaoSincronizador(
                clima, sensorRepository, leituraRepository, leituraService, cadastro, 2, 168, relogio));
        assertThrows(IllegalArgumentException.class, () -> new EstacaoSincronizador(
                clima, sensorRepository, leituraRepository, leituraService, cadastro, 72, 3000, relogio));
    }
}
