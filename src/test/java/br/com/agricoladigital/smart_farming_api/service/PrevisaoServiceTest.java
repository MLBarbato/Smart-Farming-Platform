package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.client.ClimaClient;
import br.com.agricoladigital.smart_farming_api.client.DiaPrevisto;
import br.com.agricoladigital.smart_farming_api.dto.response.PrevisaoFazendaResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PrevisaoTempoRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrevisaoServiceTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);

    @Mock private FazendaRepository fazendaRepository;
    @Mock private PrevisaoTempoRepository previsaoRepository;
    @Mock private PrevisaoArmazenamento armazenamento;
    @Mock private ClimaClient clima;

    private PrevisaoService servico;
    private Fazenda fazenda;

    @BeforeEach
    void preparar() {
        servico = new PrevisaoService(fazendaRepository, previsaoRepository, armazenamento, clima, 7, 12, 5,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("Santa Maria", "Rio Verde", 100.0, -17.79, -50.92, "Rio Verde", "GO", dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        lenient().when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        lenient().when(clima.fonte()).thenReturn("OPEN_METEO");
    }

    private PrevisaoTempo dia(LocalDate data, LocalDateTime consultadaEm) {
        return new PrevisaoTempo(fazenda, data, 24.0, 18.0, 30.0, 2.0, 40, "OPEN_METEO", consultadaEm);
    }

    @Test
    void semCoordenadasInformaSemCoordenadas() {
        Fazenda semCoord = new Fazenda("X", null, 10.0, null, null, null, null, fazenda.getUsuario());
        ReflectionTestUtils.setField(semCoord, "id", 2L);
        when(fazendaRepository.findById(2L)).thenReturn(Optional.of(semCoord));

        PrevisaoFazendaResponse r = servico.consultar(2L);

        assertEquals(SituacaoPrevisao.SEM_COORDENADAS, r.situacao());
        assertTrue(r.dias().isEmpty());
        assertNotNull(r.atribuicao());
    }

    @Test
    void semPrevisaoGravadaInformaSemDados() {
        when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenReturn(List.of());

        PrevisaoFazendaResponse r = servico.consultar(1L);

        assertEquals(SituacaoPrevisao.SEM_DADOS, r.situacao());
        assertNull(r.consultadaEm());
    }

    @Test
    void previsaoRecenteEAtualEMostraIdadeEDias() {
        LocalDateTime consulta = AGORA.minusHours(2);
        when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenReturn(List.of(
                dia(LocalDate.of(2026, 11, 16), consulta), dia(LocalDate.of(2026, 11, 17), consulta)));

        PrevisaoFazendaResponse r = servico.consultar(1L);

        assertEquals(SituacaoPrevisao.ATUAL, r.situacao());
        assertEquals("OPEN_METEO", r.fonte());
        assertEquals(2, r.dias().size());
        assertEquals(consulta, r.consultadaEm().toLocalDateTime());
        assertEquals(ZoneOffset.UTC, r.consultadaEm().getOffset());
        assertEquals(12, r.validadeHoras());
    }

    @Test
    void previsaoVelhaFicaDesatualizadaMasLimiteDeValidadeAindaEAtual() {
        when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenReturn(List.of(
                dia(LocalDate.of(2026, 11, 16), AGORA.minusHours(12))));
        assertEquals(SituacaoPrevisao.ATUAL, servico.consultar(1L).situacao());

        when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenReturn(List.of(
                dia(LocalDate.of(2026, 11, 16), AGORA.minusHours(12).minusSeconds(1))));
        assertEquals(SituacaoPrevisao.DESATUALIZADA, servico.consultar(1L).situacao());
    }

    @Test
    void diasPassadosNuncaSaoDevolvidos() {
        LocalDateTime consulta = AGORA.minusHours(1);
        when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenReturn(List.of(
                dia(LocalDate.of(2026, 11, 10), consulta), dia(LocalDate.of(2026, 11, 15), consulta),
                dia(LocalDate.of(2026, 11, 16), consulta)));

        List<LocalDate> datas = servico.consultar(1L).dias().stream().map(PrevisaoFazendaResponse.Dia::data).toList();

        assertEquals(List.of(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16)), datas);
    }

    @Test
    void somentePrevisaoCompletamentePassadaViraSemDados() {
        when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenReturn(List.of(
                dia(LocalDate.of(2026, 11, 1), AGORA.minusDays(15))));

        assertEquals(SituacaoPrevisao.SEM_DADOS, servico.consultar(1L).situacao());
    }

    @Test
    void atualizarConsultaOProvedorEGravaComFonteEInstanteUtc() {
        List<DiaPrevisto> previsao = List.of(new DiaPrevisto(LocalDate.of(2026, 11, 16), 24.0, 18.0, 30.0, 0.0, 10));
        when(clima.buscarPrevisaoDiaria(-17.79, -50.92, 7)).thenReturn(previsao);

        servico.atualizar(fazenda);

        verify(armazenamento).substituir(1L, previsao, "OPEN_METEO", AGORA);
    }

    @Test
    void falhaDoProvedorPropagaSemGravarNada() {
        when(clima.buscarPrevisaoDiaria(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new PrevisaoIndisponivelException("fora do ar"));

        assertThrows(PrevisaoIndisponivelException.class, () -> servico.atualizar(fazenda));
        verifyNoInteractions(armazenamento);
    }

    @Test
    void atualizacaoManualSemCoordenadasRetorna400() {
        Fazenda semCoord = new Fazenda("X", null, 10.0, null, null, null, null, fazenda.getUsuario());
        ReflectionTestUtils.setField(semCoord, "id", 2L);
        when(fazendaRepository.findById(2L)).thenReturn(Optional.of(semCoord));

        assertThrows(RegraDeNegocioException.class, () -> servico.atualizarManualmente(2L));
        verifyNoInteractions(clima);
    }

    @Test
    void atualizacaoManualRecenteNaoChamaOProvedor() {
        when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenReturn(List.of(
                dia(LocalDate.of(2026, 11, 16), AGORA.minusMinutes(4))));

        PrevisaoFazendaResponse r = servico.atualizarManualmente(1L);

        assertEquals(SituacaoPrevisao.ATUAL, r.situacao());
        verifyNoInteractions(clima);
    }

    @Test
    void atualizacaoManualAposOIntervaloChamaOProvedor() {
        when(previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(1L)).thenReturn(List.of(
                dia(LocalDate.of(2026, 11, 16), AGORA.minusMinutes(6))));
        when(clima.buscarPrevisaoDiaria(-17.79, -50.92, 7)).thenReturn(List.of());

        servico.atualizarManualmente(1L);

        verify(armazenamento).substituir(eq(1L), any(), eq("OPEN_METEO"), eq(AGORA));
    }

    @Test
    void fazendaInexistenteRetornaNaoEncontrado() {
        when(fazendaRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> servico.consultar(9L));
        assertThrows(RecursoNaoEncontradoException.class, () -> servico.atualizarManualmente(9L));
    }

    @Test
    void configuracaoInvalidaEhRejeitada() {
        Clock relogio = Clock.systemUTC();
        assertThrows(IllegalArgumentException.class, () -> new PrevisaoService(
                fazendaRepository, previsaoRepository, armazenamento, clima, 0, 12, 5, relogio));
        assertThrows(IllegalArgumentException.class, () -> new PrevisaoService(
                fazendaRepository, previsaoRepository, armazenamento, clima, 17, 12, 5, relogio));
        assertThrows(IllegalArgumentException.class, () -> new PrevisaoService(
                fazendaRepository, previsaoRepository, armazenamento, clima, 7, 0, 5, relogio));
    }
}
