package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.RegraAlertaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.RegraAlertaResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.RegraAlertaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegraAlertaServiceTest {
    @Mock private RegraAlertaRepository repository;
    @InjectMocks private RegraAlertaService servico;

    private RegraAlerta secaBaixa;
    private RegraAlerta secaMedia;
    private RegraAlerta secaAlta;
    private RegraAlerta chuvaBaixa;
    private RegraAlerta chuvaMedia;
    private RegraAlerta falhaMedia;

    private static RegraAlerta regra(long id, EventoAlerta e, CriticidadeAlerta c, OperadorRegra o,
                                     VariavelMonitorada v, double limiar, String un, Integer janela) {
        RegraAlerta regra = new RegraAlerta(e, c, o, v, limiar, un, janela, "d");
        ReflectionTestUtils.setField(regra, "id", id);
        return regra;
    }

    @BeforeEach
    void preparar() {
        secaBaixa = regra(1, EventoAlerta.SECA, CriticidadeAlerta.BAIXA, OperadorRegra.MENOR_QUE, VariavelMonitorada.UMIDADE_SOLO, 30, "%", 3);
        secaMedia = regra(2, EventoAlerta.SECA, CriticidadeAlerta.MEDIA, OperadorRegra.MENOR_QUE, VariavelMonitorada.UMIDADE_SOLO, 25, "%", 3);
        secaAlta = regra(3, EventoAlerta.SECA, CriticidadeAlerta.ALTA, OperadorRegra.MENOR_QUE, VariavelMonitorada.UMIDADE_SOLO, 20, "%", 3);
        chuvaBaixa = regra(4, EventoAlerta.EXCESSO_PRECIPITACAO, CriticidadeAlerta.BAIXA, OperadorRegra.MAIOR_OU_IGUAL, VariavelMonitorada.PRECIPITACAO, 30, "mm", 24);
        chuvaMedia = regra(5, EventoAlerta.EXCESSO_PRECIPITACAO, CriticidadeAlerta.MEDIA, OperadorRegra.MAIOR_OU_IGUAL, VariavelMonitorada.PRECIPITACAO, 50, "mm", 24);
        falhaMedia = regra(6, EventoAlerta.FALHA_SENSOR, CriticidadeAlerta.MEDIA, OperadorRegra.MAIOR_QUE, null, 30, "min", null);
        lenient().when(repository.findAllByEvento(EventoAlerta.SECA)).thenReturn(List.of(secaBaixa, secaMedia, secaAlta));
        lenient().when(repository.findAllByEvento(EventoAlerta.EXCESSO_PRECIPITACAO)).thenReturn(List.of(chuvaBaixa, chuvaMedia));
        lenient().when(repository.findAllByEvento(EventoAlerta.FALHA_SENSOR)).thenReturn(List.of(falhaMedia));
        lenient().when(repository.findById(anyLong())).thenReturn(Optional.empty());
        for (RegraAlerta r : List.of(secaBaixa, secaMedia, secaAlta, chuvaBaixa, chuvaMedia, falhaMedia)) {
            lenient().when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        }
    }

    @Test
    void listaOrdenadaPorEventoECriticidadeAscendente() {
        when(repository.findAll()).thenReturn(List.of(falhaMedia, secaAlta, chuvaBaixa, secaBaixa, secaMedia));

        List<RegraAlertaResponse> regras = servico.listar(null, null);

        assertEquals(List.of(1L, 2L, 3L, 4L, 6L), regras.stream().map(RegraAlertaResponse::id).toList());
    }

    @Test
    void listaFiltraPorEventoEAtiva() {
        secaMedia.atualizar(25.0, 3, false, "d");

        assertEquals(2, servico.listar(EventoAlerta.SECA, true).size());
        assertEquals(1, servico.listar(EventoAlerta.SECA, false).size());
    }

    @Test
    void buscaInexistenteRetornaNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> servico.buscar(99L));
    }

    @Test
    void atualizaLimiarJanelaAtivaEDescricao() {
        RegraAlertaResponse r = servico.atualizar(2L, new RegraAlertaRequest(26.0, 6, false, "nova"));

        assertEquals(26.0, r.limiar());
        assertEquals(6, r.janelaHoras());
        assertFalse(r.ativa());
        assertEquals("nova", r.descricao());
        assertEquals(EventoAlerta.SECA, r.evento());
        assertEquals(OperadorRegra.MENOR_QUE, r.operador());
    }

    @Test
    void seMenorQueAMaisSeveraPrecisaSerMaisBaixa() {
        // MEDIA (25) não pode chegar a 20 (empate com ALTA) nem a 31 (acima de BAIXA = 30)
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(2L, new RegraAlertaRequest(20.0, 3, true, null)));
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(2L, new RegraAlertaRequest(30.0, 3, true, null)));
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(2L, new RegraAlertaRequest(31.0, 3, true, null)));
        assertDoesNotThrow(() -> servico.atualizar(2L, new RegraAlertaRequest(21.0, 3, true, null)));
        assertDoesNotThrow(() -> servico.atualizar(2L, new RegraAlertaRequest(29.0, 3, true, null)));
    }

    @Test
    void seMaiorOuIgualAMaisSeveraPrecisaSerMaisAlta() {
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(5L, new RegraAlertaRequest(30.0, 24, true, null)));
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(4L, new RegraAlertaRequest(50.0, 24, true, null)));
        assertDoesNotThrow(() -> servico.atualizar(4L, new RegraAlertaRequest(49.0, 24, true, null)));
    }

    @Test
    void limiarPrecisaEstarNaFaixaDaVariavel() {
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(1L, new RegraAlertaRequest(150.0, 3, true, null)));
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(3L, new RegraAlertaRequest(-1.0, 3, true, null)));
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(1L, new RegraAlertaRequest(Double.NaN, 3, true, null)));
    }

    @Test
    void janelaObrigatoriaNosEventosComJanelaEProibidaNaFalhaDeSensor() {
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(1L, new RegraAlertaRequest(30.0, null, true, null)));
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(6L, new RegraAlertaRequest(30.0, 3, true, null)));
        assertDoesNotThrow(() -> servico.atualizar(6L, new RegraAlertaRequest(45.0, null, true, null)));
    }

    @Test
    void limiarDaFalhaDeSensorEmMinutosPositivosAteSeteDias() {
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(6L, new RegraAlertaRequest(0.0, null, true, null)));
        assertThrows(RegraDeNegocioException.class, () -> servico.atualizar(6L, new RegraAlertaRequest(10_081.0, null, true, null)));
        assertDoesNotThrow(() -> servico.atualizar(6L, new RegraAlertaRequest(10_080.0, null, true, null)));
    }

    @Test
    void regraInexistenteNaoAtualiza() {
        assertThrows(RecursoNaoEncontradoException.class, () -> servico.atualizar(99L, new RegraAlertaRequest(1.0, 3, true, null)));
    }
}
