package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.PaginaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.RecomendacaoIrrigacaoResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import br.com.agricoladigital.smart_farming_api.repository.RecomendacaoIrrigacaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecomendacaoIrrigacaoServiceTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);

    @Mock private RecomendacaoIrrigacaoRepository recomendacaoRepository;
    @Mock private RecomendadorIrrigacao recomendador;
    @Mock private FazendaRepository fazendaRepository;
    @Mock private TalhaoRepository talhaoRepository;
    @Mock private PlantioRepository plantioRepository;

    private RecomendacaoIrrigacaoService service;
    private Talhao t1;
    private Talhao t2;

    @BeforeEach
    void preparar() {
        service = new RecomendacaoIrrigacaoService(recomendacaoRepository, recomendador, fazendaRepository,
                talhaoRepository, plantioRepository, Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        Fazenda fazenda = new Fazenda("F", null, 10.0, null, null, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", 7L);
        t1 = new Talhao("A1", 5.0, null, fazenda);
        ReflectionTestUtils.setField(t1, "id", 10L);
        t2 = new Talhao("B1", 5.0, null, fazenda);
        ReflectionTestUtils.setField(t2, "id", 11L);
        lenient().when(fazendaRepository.existsById(7L)).thenReturn(true);
    }

    private RecomendacaoIrrigacao recomendacao(Talhao talhao, long id, SituacaoIrrigacao situacao, LocalDateTime validoAte) {
        InsumosIrrigacao insumos = new InsumosIrrigacao("Soja", 30.0, 38.0, 30, 28.0, OrigemUmidade.SENSOR_TALHAO,
                30.0, 5.0, 4.0, 31.0, false,
                situacao == SituacaoIrrigacao.SEM_DADOS_SUFICIENTES ? List.of("previsão do tempo atualizada") : List.of());
        PrevisaoTempo previsao = new PrevisaoTempo(null, LocalDate.of(2026, 11, 16), 20.0, 15.0, 30.0, 3.0, 50,
                "Open-Meteo", AGORA.minusHours(1));
        RecomendacaoIrrigacao r = new RecomendacaoIrrigacao(talhao, previsao, AGORA.minusHours(1), validoAte, situacao,
                situacao == SituacaoIrrigacao.IRRIGAR ? 26.0 : null, "justificativa", insumos);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private Plantio plantio(Talhao talhao) {
        return new Plantio(talhao, new Cultura("Soja", 120), "Direto", "2026/2027", LocalDate.of(2026, 10, 1), null);
    }

    @Test
    void respostaTrazOsInsumosDoCalculoEAAtribuicaoDaPrevisao() {
        RecomendacaoIrrigacao r = recomendacao(t1, 1L, SituacaoIrrigacao.IRRIGAR, AGORA.plusHours(5));
        when(recomendacaoRepository.findByIdAndTalhao_Fazenda_Id(1L, 7L)).thenReturn(Optional.of(r));

        RecomendacaoIrrigacaoResponse resposta = service.buscar(7L, 1L);

        assertEquals(SituacaoIrrigacao.IRRIGAR, resposta.situacao());
        assertEquals(26.0, resposta.volumeRecomendadoMm());
        assertFalse(resposta.vencida());
        assertEquals("Soja", resposta.cultura().nome());
        assertEquals(30, resposta.cultura().profundidadeRaizCm());
        assertEquals(28.0, resposta.umidade().atual());
        assertEquals(OrigemUmidade.SENSOR_TALHAO, resposta.umidade().origem());
        assertEquals(30.0, resposta.deficitMm());
        assertEquals(5.0, resposta.chuva().previstaMm());
        assertEquals(4.0, resposta.chuva().efetivaMm());
        assertEquals("Open-Meteo", resposta.previsao().fonte());
        assertEquals(PrevisaoService.ATRIBUICAO, resposta.previsao().atribuicao());
        assertTrue(resposta.dadosFaltantes().isEmpty());
        assertEquals(AGORA.plusHours(5).atOffset(ZoneOffset.UTC), resposta.validoAte());
    }

    @Test
    void recomendacaoVencidaEMarcadaEDadosFaltantesVoltam() {
        RecomendacaoIrrigacao r = recomendacao(t1, 1L, SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, AGORA);
        when(recomendacaoRepository.findByIdAndTalhao_Fazenda_Id(1L, 7L)).thenReturn(Optional.of(r));

        RecomendacaoIrrigacaoResponse resposta = service.buscar(7L, 1L);

        assertTrue(resposta.vencida());
        assertEquals(List.of("previsão do tempo atualizada"), resposta.dadosFaltantes());
        assertNull(resposta.volumeRecomendadoMm());
    }

    @Test
    void recomendacaoDeOutraFazendaNaoEEncontrada() {
        when(recomendacaoRepository.findByIdAndTalhao_Fazenda_Id(1L, 8L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(8L, 1L));
    }

    @Test
    void atuaisTrazSoTalhoesEmCultivoOrdenadosPorIdentificacao() {
        when(plantioRepository.buscarPorFazendaEStatus(7L, StatusPlantio.EM_CULTIVO))
                .thenReturn(List.of(plantio(t1), plantio(t2)));
        Talhao encerrado = new Talhao("C1", 5.0, null, t1.getFazenda());
        ReflectionTestUtils.setField(encerrado, "id", 12L);
        when(recomendacaoRepository.ultimasDaFazenda(7L)).thenReturn(List.of(
                recomendacao(t2, 2L, SituacaoIrrigacao.NAO_IRRIGAR, AGORA.plusHours(5)),
                recomendacao(encerrado, 3L, SituacaoIrrigacao.IRRIGAR, AGORA.plusHours(5)),
                recomendacao(t1, 1L, SituacaoIrrigacao.IRRIGAR, AGORA.plusHours(5))));

        List<RecomendacaoIrrigacaoResponse> atuais = service.atuais(7L);

        assertEquals(List.of("A1", "B1"), atuais.stream().map(RecomendacaoIrrigacaoResponse::talhaoIdentificacao).toList());
    }

    @Test
    void calcularRecalculaEDevolveAsAtuais() {
        when(plantioRepository.buscarPorFazendaEStatus(7L, StatusPlantio.EM_CULTIVO)).thenReturn(List.of(plantio(t1)));
        when(recomendacaoRepository.ultimasDaFazenda(7L)).thenReturn(List.of(
                recomendacao(t1, 1L, SituacaoIrrigacao.IRRIGAR, AGORA.plusHours(5))));

        assertEquals(1, service.calcular(7L).size());
        verify(recomendador).calcularFazenda(7L);
    }

    @Test
    void fazendaInexistenteRetorna404SemCalcular() {
        when(fazendaRepository.existsById(9L)).thenReturn(false);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.calcular(9L));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.atuais(9L));
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.listar(9L, null, null, null, null, null, null));
        verifyNoInteractions(recomendador);
    }

    @Test
    void listagemValidaParametros() {
        OffsetDateTime a = AGORA.atOffset(ZoneOffset.UTC);
        assertThrows(RegraDeNegocioException.class, () -> service.listar(7L, null, null, null, null, -1, null));
        assertThrows(RegraDeNegocioException.class, () -> service.listar(7L, null, null, null, null, 0, 0));
        assertThrows(RegraDeNegocioException.class, () -> service.listar(7L, null, null, null, null, 0, 101));
        assertThrows(RegraDeNegocioException.class, () -> service.listar(7L, null, null, a, a.minusDays(1), null, null));
        when(talhaoRepository.findByIdAndFazenda_Id(55L, 7L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.listar(7L, 55L, null, null, null, null, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listagemPaginaOrdenadaDoMaisRecenteParaOMaisAntigo() {
        Pageable[] capturado = new Pageable[1];
        when(recomendacaoRepository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(i -> {
            capturado[0] = i.getArgument(1);
            return new PageImpl<>(List.of(recomendacao(t1, 1L, SituacaoIrrigacao.IRRIGAR, AGORA.plusHours(5))),
                    capturado[0], 25);
        });

        PaginaResponse<RecomendacaoIrrigacaoResponse> pagina =
                service.listar(7L, null, SituacaoIrrigacao.IRRIGAR, null, null, 0, null);

        assertEquals(20, pagina.tamanho());
        assertEquals(25, pagina.totalElementos());
        assertEquals(2, pagina.totalPaginas());
        assertEquals("dataHora: DESC,id: DESC", capturado[0].getSort().toString());
    }
}
