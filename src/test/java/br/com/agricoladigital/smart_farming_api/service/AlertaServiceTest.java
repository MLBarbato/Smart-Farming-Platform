package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusAlertaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.AlertaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.PaginaResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);

    @Mock private AlertaRepository alertaRepository;
    @Mock private FazendaRepository fazendaRepository;
    @Mock private TalhaoRepository talhaoRepository;
    @Mock private UsuarioRepository usuarioRepository;

    private AlertaService service;
    private Usuario ana;
    private Talhao talhao;
    private RegraAlerta regra;

    @BeforeEach
    void preparar() {
        service = new AlertaService(alertaRepository, fazendaRepository, talhaoRepository, usuarioRepository,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        ana = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        ReflectionTestUtils.setField(ana, "id", 1L);
        Fazenda fazenda = new Fazenda("F", null, 10.0, null, null, null, null, ana);
        ReflectionTestUtils.setField(fazenda, "id", 7L);
        talhao = new Talhao("T1", 5.0, null, fazenda);
        ReflectionTestUtils.setField(talhao, "id", 10L);
        regra = new RegraAlerta(EventoAlerta.SECA, CriticidadeAlerta.MEDIA, OperadorRegra.MENOR_QUE,
                VariavelMonitorada.UMIDADE_SOLO, 25.0, "%", 3, "d");
        ReflectionTestUtils.setField(regra, "id", 3L);
    }

    private Alerta alerta(StatusAlerta status) {
        Alerta alerta = new Alerta(regra, talhao, null, AGORA.minusHours(2), "Seca: teste");
        ReflectionTestUtils.setField(alerta, "id", 99L);
        ReflectionTestUtils.setField(alerta, "status", status);
        return alerta;
    }

    private void alertaDaFazenda(Alerta alerta) {
        when(alertaRepository.findByIdAndTalhao_Fazenda_Id(99L, 7L)).thenReturn(Optional.of(alerta));
    }

    private AlterarStatusAlertaRequest pedido(StatusAlerta status, String nota) {
        return new AlterarStatusAlertaRequest(status, nota);
    }

    private void usuarioExiste() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(ana));
    }

    @Test
    void assumirRegistraAtendenteEHorarioEMantemAbertoParaTratamento() {
        Alerta alerta = alerta(StatusAlerta.ABERTO);
        alertaDaFazenda(alerta);
        usuarioExiste();

        AlertaResponse resposta = service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.EM_ATENDIMENTO, "  a caminho "));

        assertEquals(StatusAlerta.EM_ATENDIMENTO, resposta.status());
        assertEquals("Ana", resposta.atendente().nome());
        assertEquals(AGORA.atOffset(ZoneOffset.UTC), resposta.atendidoEm());
        assertEquals("a caminho", resposta.nota());
        assertNull(resposta.resolvidoEm());
        assertNull(resposta.resolucao());
    }

    @Test
    void resolverDepoisDeAssumirMantemOPrimeiroAtendimento() {
        Alerta alerta = alerta(StatusAlerta.ABERTO);
        alertaDaFazenda(alerta);
        usuarioExiste();
        service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.EM_ATENDIMENTO, null));
        ReflectionTestUtils.setField(alerta, "atendidoEm", AGORA.minusMinutes(30));

        AlertaResponse resposta = service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.RESOLVIDO, "irrigado"));

        assertEquals(StatusAlerta.RESOLVIDO, resposta.status());
        assertEquals(ResolucaoAlerta.MANUAL, resposta.resolucao());
        assertEquals(AGORA.atOffset(ZoneOffset.UTC), resposta.resolvidoEm());
        assertEquals(AGORA.minusMinutes(30).atOffset(ZoneOffset.UTC), resposta.atendidoEm());
    }

    @Test
    void resolverDiretamenteDeAbertoRegistraOAtendimento() {
        alertaDaFazenda(alerta(StatusAlerta.ABERTO));
        usuarioExiste();

        AlertaResponse resposta = service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.RESOLVIDO, null));

        assertEquals(StatusAlerta.RESOLVIDO, resposta.status());
        assertNotNull(resposta.atendidoEm());
        assertEquals(1L, resposta.atendente().id());
    }

    @Test
    void dispensarExigeNota() {
        alertaDaFazenda(alerta(StatusAlerta.ABERTO));

        assertThrows(RegraDeNegocioException.class,
                () -> service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.DISPENSADO, null)));
        assertThrows(RegraDeNegocioException.class,
                () -> service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.DISPENSADO, "   ")));
    }

    @Test
    void dispensarComNotaFechaSemResolucao() {
        alertaDaFazenda(alerta(StatusAlerta.EM_ATENDIMENTO));
        usuarioExiste();

        AlertaResponse resposta = service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.DISPENSADO, "sensor descalibrado"));

        assertEquals(StatusAlerta.DISPENSADO, resposta.status());
        assertNull(resposta.resolucao());
        assertNotNull(resposta.resolvidoEm());
        assertEquals("sensor descalibrado", resposta.nota());
    }

    @Test
    void transicoesForaDoDiagramaRetornamConflito() {
        for (StatusAlerta fechado : List.of(StatusAlerta.RESOLVIDO, StatusAlerta.DISPENSADO)) {
            alertaDaFazenda(alerta(fechado));
            for (StatusAlerta destino : StatusAlerta.values()) {
                assertThrows(ConflitoException.class,
                        () -> service.alterarStatus(7L, 99L, 1L, pedido(destino, "x")));
            }
        }
        alertaDaFazenda(alerta(StatusAlerta.EM_ATENDIMENTO));
        assertThrows(ConflitoException.class, () -> service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.ABERTO, null)));
        assertThrows(ConflitoException.class,
                () -> service.alterarStatus(7L, 99L, 1L, pedido(StatusAlerta.EM_ATENDIMENTO, null)));
    }

    @Test
    void alertaDeOutraFazendaNaoE_encontrado() {
        when(alertaRepository.findByIdAndTalhao_Fazenda_Id(99L, 8L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(8L, 99L));
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.alterarStatus(8L, 99L, 1L, pedido(StatusAlerta.RESOLVIDO, null)));
    }

    @Test
    void buscarDevolveRegraLeituraEAtendente() {
        Alerta alerta = alerta(StatusAlerta.ABERTO);
        Dispositivo d = new Dispositivo("SOLO-01", "SENSOR", null, StatusDispositivo.ATIVO, talhao);
        ReflectionTestUtils.setField(alerta, "leitura", new Leitura(d, 18.0, AGORA.minusMinutes(5), OrigemLeitura.SIMULADA));
        alertaDaFazenda(alerta);

        AlertaResponse r = service.buscar(7L, 99L);

        assertEquals("T1", r.talhaoIdentificacao());
        assertEquals(CriticidadeAlerta.MEDIA, r.criticidade());
        assertEquals(25.0, r.regra().limiar());
        assertEquals("SOLO-01", r.leitura().dispositivo());
        assertEquals(18.0, r.leitura().valor());
        assertNull(r.atendente());
        assertEquals(AGORA.minusHours(2).atOffset(ZoneOffset.UTC), r.dataHora());
    }

    // ---- listagem ----

    @Test
    void listagemValidaPaginacaoEPeriodo() {
        when(fazendaRepository.existsById(7L)).thenReturn(true);

        assertThrows(RegraDeNegocioException.class,
                () -> service.listar(7L, null, null, null, null, null, null, -1, null));
        assertThrows(RegraDeNegocioException.class,
                () -> service.listar(7L, null, null, null, null, null, null, 0, 0));
        assertThrows(RegraDeNegocioException.class,
                () -> service.listar(7L, null, null, null, null, null, null, 0, 101));
        OffsetDateTime a = AGORA.atOffset(ZoneOffset.UTC);
        assertThrows(RegraDeNegocioException.class,
                () -> service.listar(7L, null, null, null, null, a, a.minusDays(1), null, null));
    }

    @Test
    void listagemDeFazendaOuTalhaoInexistenteRetorna404() {
        when(fazendaRepository.existsById(8L)).thenReturn(false);
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.listar(8L, null, null, null, null, null, null, null, null));

        when(fazendaRepository.existsById(7L)).thenReturn(true);
        when(talhaoRepository.findByIdAndFazenda_Id(55L, 7L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.listar(7L, null, null, null, 55L, null, null, null, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listagemDevolvePaginaOrdenadaDoMaisRecenteParaOMaisAntigo() {
        when(fazendaRepository.existsById(7L)).thenReturn(true);
        Pageable[] capturado = new Pageable[1];
        when(alertaRepository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(i -> {
            capturado[0] = i.getArgument(1);
            return new PageImpl<>(List.of(alerta(StatusAlerta.ABERTO)), capturado[0], 41);
        });

        PaginaResponse<AlertaResponse> pagina = service.listar(7L, List.of(StatusAlerta.ABERTO), EventoAlerta.SECA,
                CriticidadeAlerta.MEDIA, null, null, null, 1, 20);

        assertEquals(1, pagina.pagina());
        assertEquals(20, pagina.tamanho());
        assertEquals(41, pagina.totalElementos());
        assertEquals(3, pagina.totalPaginas());
        assertEquals(1, pagina.conteudo().size());
        assertEquals("dataHora: DESC,id: DESC", capturado[0].getSort().toString());
        assertEquals(PageRequest.of(1, 20).getOffset(), capturado[0].getOffset());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listagemUsaTamanhoPadraoQuandoNaoInformado() {
        when(fazendaRepository.existsById(7L)).thenReturn(true);
        when(alertaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<Alerta>(List.of(), i.getArgument(1), 0));

        assertEquals(20, service.listar(7L, null, null, null, null, null, null, null, null).tamanho());
        verify(talhaoRepository, never()).findByIdAndFazenda_Id(anyLong(), anyLong());
    }

    // ---- listagem global (todas as fazendas do usuário) ----

    @Test
    @SuppressWarnings("unchecked")
    void listagemGlobalDoAgricultorDevolveAlertasComFazenda() {
        when(alertaRepository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(i ->
                new PageImpl<>(List.of(alerta(StatusAlerta.ABERTO)), i.getArgument(1), 1));

        PaginaResponse<AlertaResponse> pagina = service.listarDoUsuario(1L, false, null, null, null, null, null,
                null, null, null, null);

        assertEquals(1, pagina.totalElementos());
        assertEquals(7L, pagina.conteudo().get(0).fazendaId());
        assertEquals("F", pagina.conteudo().get(0).fazendaNome());
        verify(fazendaRepository, never()).existsByIdAndUsuario_Id(anyLong(), anyLong());
    }

    @Test
    void fazendaDeOutroUsuarioSeComportaComoInexistenteNaListagemGlobal() {
        when(fazendaRepository.existsByIdAndUsuario_Id(9L, 1L)).thenReturn(false);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.listarDoUsuario(1L, false, 9L, null, null,
                null, null, null, null, null, null));
        verify(alertaRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void administradorVerificaSoSeAFazendaExiste() {
        when(fazendaRepository.existsById(9L)).thenReturn(false);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.listarDoUsuario(2L, true, 9L, null, null,
                null, null, null, null, null, null));
        verify(fazendaRepository, never()).existsByIdAndUsuario_Id(anyLong(), anyLong());
    }

    @Test
    void listagemGlobalValidaPaginacaoEPeriodo() {
        OffsetDateTime a = AGORA.atOffset(ZoneOffset.UTC);
        assertThrows(RegraDeNegocioException.class, () -> service.listarDoUsuario(1L, false, null, null, null, null,
                null, null, null, -1, null));
        assertThrows(RegraDeNegocioException.class, () -> service.listarDoUsuario(1L, false, null, null, null, null,
                null, a, a.minusDays(1), null, null));
        assertThrows(RegraDeNegocioException.class, () -> service.listarDoUsuario(1L, false, null, null, null, null,
                null, null, null, 0, 101));
    }
}
