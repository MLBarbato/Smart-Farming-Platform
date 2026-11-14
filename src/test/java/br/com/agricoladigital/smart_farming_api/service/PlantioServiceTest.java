package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusPlantioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.PlantioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.RegistrarColheitaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.PlantioResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.CulturaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlantioServiceTest {
    private static final LocalDate PLANTIO = LocalDate.of(2026, 3, 1);

    @Mock private PlantioRepository plantioRepository;
    @Mock private TalhaoRepository talhaoRepository;
    @Mock private CulturaRepository culturaRepository;
    @InjectMocks private PlantioService plantioService;

    private Talhao talhao;
    private Cultura soja;

    @BeforeEach
    void preparar() {
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        Fazenda fazenda = new Fazenda("Horizonte", "GO", 100.0, null, null, null, null, dono);
        talhao = new Talhao("T1", 10.0, null, fazenda);
        ReflectionTestUtils.setField(talhao, "id", 5L);
        soja = new Cultura("Soja", 120);
        ReflectionTestUtils.setField(soja, "id", 7L);
    }

    private void talhaoExiste() {
        when(talhaoRepository.findByIdAndFazenda_Id(5L, 1L)).thenReturn(Optional.of(talhao));
    }

    private Plantio plantio(StatusPlantio status) {
        Plantio plantio = new Plantio(talhao, soja, "Direto", "2025/2026", PLANTIO, PLANTIO.plusDays(120));
        ReflectionTestUtils.setField(plantio, "id", 9L);
        ReflectionTestUtils.setField(plantio, "status", status);
        talhaoExiste();
        when(plantioRepository.findByIdAndTalhao_Id(9L, 5L)).thenReturn(Optional.of(plantio));
        return plantio;
    }

    @Test
    void criarIniciaComoPlanejadoECalculaColheitaPeloCiclo() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));
        when(plantioRepository.save(any(Plantio.class))).thenAnswer(i -> i.getArgument(0));

        PlantioResponse response = plantioService.criar(1L, 5L, new PlantioRequest(7L, "Direto", null, PLANTIO, null));

        assertEquals(StatusPlantio.PLANEJADO, response.status());
        assertEquals(PLANTIO.plusDays(120), response.dataColheitaPrevista());
        assertEquals("Soja", response.culturaNome());
    }

    @Test
    void criarGuardaOTipoDePlantioNoPlantio() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));
        when(plantioRepository.save(any(Plantio.class))).thenAnswer(i -> i.getArgument(0));

        PlantioResponse response = plantioService.criar(
                1L, 5L, new PlantioRequest(7L, "Plantio convencional", null, PLANTIO, null));

        assertEquals("Plantio convencional", response.tipoPlantio());
    }

    @Test
    void criarMantemColheitaInformadaMesmoComCiclo() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));
        when(plantioRepository.save(any(Plantio.class))).thenAnswer(i -> i.getArgument(0));

        PlantioResponse response = plantioService.criar(
                1L, 5L, new PlantioRequest(7L, "Direto", null, PLANTIO, PLANTIO.plusDays(90)));

        assertEquals(PLANTIO.plusDays(90), response.dataColheitaPrevista());
    }

    @Test
    void criarRejeitaColheitaAnteriorAoPlantio() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));

        assertThrows(RegraDeNegocioException.class, () -> plantioService.criar(
                1L, 5L, new PlantioRequest(7L, "Direto", null, PLANTIO, PLANTIO.minusDays(1))));
        verify(plantioRepository, never()).save(any());
    }

    @Test
    void criarRejeitaTalhaoDeOutraFazenda() {
        when(talhaoRepository.findByIdAndFazenda_Id(5L, 2L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> plantioService.criar(2L, 5L, new PlantioRequest(7L, "Direto", null, PLANTIO, null)));
    }

    @Test
    void criarRejeitaCulturaInexistente() {
        talhaoExiste();
        when(culturaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> plantioService.criar(1L, 5L, new PlantioRequest(99L, "Direto", null, PLANTIO, null)));
    }

    @Test
    void iniciarCultivoMudaOStatus() {
        Plantio plantio = plantio(StatusPlantio.PLANEJADO);
        when(plantioRepository.existsByTalhao_IdAndStatus(5L, StatusPlantio.EM_CULTIVO)).thenReturn(false);

        plantioService.alterarStatus(1L, 5L, 9L, new AlterarStatusPlantioRequest(StatusPlantio.EM_CULTIVO, null, null));

        assertEquals(StatusPlantio.EM_CULTIVO, plantio.getStatus());
    }

    @Test
    void iniciarCultivoRejeitaTalhaoComOutroPlantioEmCultivo() {
        Plantio plantio = plantio(StatusPlantio.PLANEJADO);
        when(plantioRepository.existsByTalhao_IdAndStatus(5L, StatusPlantio.EM_CULTIVO)).thenReturn(true);

        assertThrows(ConflitoException.class, () -> plantioService.alterarStatus(
                1L, 5L, 9L, new AlterarStatusPlantioRequest(StatusPlantio.EM_CULTIVO, null, null)));

        assertEquals(StatusPlantio.PLANEJADO, plantio.getStatus());
    }

    @Test
    void transicoesInvalidasSaoRejeitadas() {
        plantio(StatusPlantio.PLANEJADO);

        assertThrows(ConflitoException.class, () -> plantioService.alterarStatus(
                1L, 5L, 9L, new AlterarStatusPlantioRequest(StatusPlantio.COLHIDO, null, null)));
    }

    @Test
    void statusFinalNaoTemMaisTransicoes() {
        plantio(StatusPlantio.COLHIDO);

        assertThrows(ConflitoException.class, () -> plantioService.alterarStatus(
                1L, 5L, 9L, new AlterarStatusPlantioRequest(StatusPlantio.EM_CULTIVO, null, null)));
    }

    @Test
    void colherAPartirDoCultivo() {
        Plantio plantio = plantio(StatusPlantio.EM_CULTIVO);

        plantioService.alterarStatus(1L, 5L, 9L, new AlterarStatusPlantioRequest(StatusPlantio.COLHIDO, null, null));

        assertEquals(StatusPlantio.COLHIDO, plantio.getStatus());
    }

    @Test
    void atualizarRejeitaPlantioFinalizado() {
        plantio(StatusPlantio.CANCELADO);

        assertThrows(ConflitoException.class,
                () -> plantioService.atualizar(1L, 5L, 9L, new PlantioRequest(7L, "Direto", null, PLANTIO, null)));
    }

    @Test
    void trocarCulturaSoEmPlantioPlanejado() {
        plantio(StatusPlantio.EM_CULTIVO);

        assertThrows(ConflitoException.class,
                () -> plantioService.atualizar(1L, 5L, 9L, new PlantioRequest(8L, "Direto", null, PLANTIO, null)));
        verify(culturaRepository, never()).findById(any());
    }

    @Test
    void atualizarAjustaDatasMantendoACultura() {
        Plantio plantio = plantio(StatusPlantio.EM_CULTIVO);
        LocalDate novaColheita = PLANTIO.plusDays(100);

        plantioService.atualizar(1L, 5L, 9L, new PlantioRequest(7L, "Direto", null, PLANTIO, novaColheita));

        assertEquals(novaColheita, plantio.getDataColheitaPrevista());
    }

    @Test
    void excluirSoPlantioPlanejado() {
        Plantio emCultivo = plantio(StatusPlantio.EM_CULTIVO);

        assertThrows(ConflitoException.class, () -> plantioService.excluir(1L, 5L, 9L));
        verify(plantioRepository, never()).delete(any(Plantio.class));

        ReflectionTestUtils.setField(emCultivo, "status", StatusPlantio.PLANEJADO);
        plantioService.excluir(1L, 5L, 9L);
        verify(plantioRepository).delete(emCultivo);
    }

    // ---- safra ----

    @Test
    void safraPadraoComecaEmJulho() {
        assertEquals("2026/2027", PlantioService.safraPadrao(LocalDate.of(2026, 9, 15)));
        assertEquals("2026/2027", PlantioService.safraPadrao(LocalDate.of(2027, 3, 1)));
        assertEquals("2026/2027", PlantioService.safraPadrao(LocalDate.of(2026, 7, 1)));
        assertEquals("2025/2026", PlantioService.safraPadrao(LocalDate.of(2026, 6, 30)));
    }

    @Test
    void criarSemSafraUsaASafraPadraoDaDataDePlantio() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));
        when(plantioRepository.save(any(Plantio.class))).thenAnswer(i -> i.getArgument(0));

        PlantioResponse response = plantioService.criar(
                1L, 5L, new PlantioRequest(7L, null, null, LocalDate.of(2026, 9, 10), null));

        assertEquals("2026/2027", response.safra());
    }

    @Test
    void criarAceitaSafraInformadaEPermiteSafrinhaNoAnoSeguinte() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));
        when(plantioRepository.save(any(Plantio.class))).thenAnswer(i -> i.getArgument(0));

        PlantioResponse response = plantioService.criar(
                1L, 5L, new PlantioRequest(7L, null, "2026/2027", LocalDate.of(2027, 2, 10), null));

        assertEquals("2026/2027", response.safra());
    }

    @Test
    void criarRejeitaSafraSemAnosConsecutivos() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));

        assertThrows(RegraDeNegocioException.class, () -> plantioService.criar(
                1L, 5L, new PlantioRequest(7L, null, "2026/2028", PLANTIO, null)));
        verify(plantioRepository, never()).save(any());
    }

    // ---- sobreposição de períodos ----

    private void haConflitoCom(Plantio outro) {
        when(plantioRepository.buscarConflitantes(eq(5L), anyLong(), any(), any(), any(), eq(StatusPlantio.CANCELADO)))
                .thenReturn(List.of(outro));
    }

    @Test
    void criarRejeitaPlantioAntesDaColheitaAnterior() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));
        Plantio anterior = new Plantio(talhao, soja, null, "2025/2026", PLANTIO, PLANTIO.plusDays(120));
        ReflectionTestUtils.setField(anterior, "id", 3L);
        haConflitoCom(anterior);

        ConflitoException erro = assertThrows(ConflitoException.class, () -> plantioService.criar(
                1L, 5L, new PlantioRequest(7L, null, null, PLANTIO.plusDays(60), null)));

        assertTrue(erro.getMessage().contains("plantio 3"));
        verify(plantioRepository, never()).save(any());
    }

    @Test
    void criarConsultaSobreposicaoComOPeriodoCalculadoPeloCiclo() {
        talhaoExiste();
        when(culturaRepository.findById(7L)).thenReturn(Optional.of(soja));
        when(plantioRepository.save(any(Plantio.class))).thenAnswer(i -> i.getArgument(0));

        plantioService.criar(1L, 5L, new PlantioRequest(7L, null, null, PLANTIO, null));

        verify(plantioRepository).buscarConflitantes(
                5L, -1L, PLANTIO, PLANTIO.plusDays(120), LocalDate.of(9999, 12, 31), StatusPlantio.CANCELADO);
    }

    @Test
    void plantioSemFimPrevistoOcupaOTalhaoEmAberto() {
        talhaoExiste();
        Cultura semCiclo = new Cultura("Pastagem", null);
        ReflectionTestUtils.setField(semCiclo, "id", 8L);
        when(culturaRepository.findById(8L)).thenReturn(Optional.of(semCiclo));
        when(plantioRepository.save(any(Plantio.class))).thenAnswer(i -> i.getArgument(0));

        plantioService.criar(1L, 5L, new PlantioRequest(8L, null, null, PLANTIO, null));

        verify(plantioRepository).buscarConflitantes(
                5L, -1L, PLANTIO, LocalDate.of(9999, 12, 31), LocalDate.of(9999, 12, 31), StatusPlantio.CANCELADO);
    }

    @Test
    void atualizarIgnoraOProprioPlantioNaVerificacao() {
        plantio(StatusPlantio.PLANEJADO);

        plantioService.atualizar(1L, 5L, 9L, new PlantioRequest(7L, null, null, PLANTIO, PLANTIO.plusDays(100)));

        verify(plantioRepository).buscarConflitantes(
                eq(5L), eq(9L), eq(PLANTIO), eq(PLANTIO.plusDays(100)), any(), eq(StatusPlantio.CANCELADO));
    }

    @Test
    void iniciarCultivoReconfereSobreposicaoComAColheitaRealAnterior() {
        Plantio proximo = plantio(StatusPlantio.PLANEJADO);
        when(plantioRepository.existsByTalhao_IdAndStatus(5L, StatusPlantio.EM_CULTIVO)).thenReturn(false);
        Plantio anterior = new Plantio(talhao, soja, null, "2025/2026", PLANTIO.minusDays(150), PLANTIO.minusDays(30));
        ReflectionTestUtils.setField(anterior, "id", 2L);
        anterior.colher(PLANTIO.plusDays(10), 1000.0);
        haConflitoCom(anterior);

        assertThrows(ConflitoException.class, () -> plantioService.alterarStatus(
                1L, 5L, 9L, new AlterarStatusPlantioRequest(StatusPlantio.EM_CULTIVO, null, null)));

        assertEquals(StatusPlantio.PLANEJADO, proximo.getStatus());
    }

    // ---- colheita real e produtividade ----

    @Test
    void colherSemDataAssumeHojeERegistraQuantidade() {
        Plantio plantio = plantio(StatusPlantio.EM_CULTIVO);

        PlantioResponse response = plantioService.alterarStatus(1L, 5L, 9L,
                new AlterarStatusPlantioRequest(StatusPlantio.COLHIDO, null, 5000.0));

        assertEquals(StatusPlantio.COLHIDO, plantio.getStatus());
        assertEquals(LocalDate.now(), response.dataColheitaReal());
        assertEquals(5000.0, response.quantidadeColhidaKg());
    }

    @Test
    void produtividadeEQuantidadePorHectareDoTalhao() {
        plantio(StatusPlantio.EM_CULTIVO);

        PlantioResponse response = plantioService.alterarStatus(1L, 5L, 9L,
                new AlterarStatusPlantioRequest(StatusPlantio.COLHIDO, PLANTIO.plusDays(110), 5000.0));

        // Talhão de 10 ha: 5000 kg / 10 ha = 500 kg/ha.
        assertEquals(500.0, response.produtividadeKgPorHectare());
        assertEquals(PLANTIO.plusDays(110), response.dataColheitaReal());
    }

    @Test
    void colherSemQuantidadeNaoCalculaProdutividade() {
        plantio(StatusPlantio.EM_CULTIVO);

        PlantioResponse response = plantioService.alterarStatus(1L, 5L, 9L,
                new AlterarStatusPlantioRequest(StatusPlantio.COLHIDO, PLANTIO.plusDays(110), null));

        assertNull(response.produtividadeKgPorHectare());
    }

    @Test
    void colherRejeitaDataAnteriorAoPlantioOuFutura() {
        plantio(StatusPlantio.EM_CULTIVO);

        assertThrows(RegraDeNegocioException.class, () -> plantioService.alterarStatus(1L, 5L, 9L,
                new AlterarStatusPlantioRequest(StatusPlantio.COLHIDO, PLANTIO.minusDays(1), null)));
        assertThrows(RegraDeNegocioException.class, () -> plantioService.alterarStatus(1L, 5L, 9L,
                new AlterarStatusPlantioRequest(StatusPlantio.COLHIDO, LocalDate.now().plusDays(1), null)));
    }

    @Test
    void dadosDeColheitaSoValemParaOStatusColhido() {
        Plantio plantio = plantio(StatusPlantio.EM_CULTIVO);

        assertThrows(RegraDeNegocioException.class, () -> plantioService.alterarStatus(1L, 5L, 9L,
                new AlterarStatusPlantioRequest(StatusPlantio.CANCELADO, null, 100.0)));

        assertEquals(StatusPlantio.EM_CULTIVO, plantio.getStatus());
        assertNull(plantio.getQuantidadeColhidaKg());
    }

    @Test
    void registrarColheitaCorrigeDataEQuantidadeDePlantioColhido() {
        Plantio plantio = plantio(StatusPlantio.COLHIDO);

        PlantioResponse response = plantioService.registrarColheita(1L, 5L, 9L,
                new RegistrarColheitaRequest(PLANTIO.plusDays(100), 7000.0));

        assertEquals(PLANTIO.plusDays(100), plantio.getDataColheitaReal());
        assertEquals(700.0, response.produtividadeKgPorHectare());
    }

    @Test
    void registrarColheitaRejeitaPlantioQueNaoFoiColhido() {
        plantio(StatusPlantio.EM_CULTIVO);

        assertThrows(ConflitoException.class, () -> plantioService.registrarColheita(1L, 5L, 9L,
                new RegistrarColheitaRequest(PLANTIO.plusDays(100), 7000.0)));
    }
}
