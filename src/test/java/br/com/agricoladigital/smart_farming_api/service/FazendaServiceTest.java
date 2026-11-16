package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.FazendaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.FazendaResponse;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PrevisaoTempoRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FazendaServiceTest {
    @Mock
    private FazendaRepository fazendaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TalhaoRepository talhaoRepository;

    @Mock
    private PrevisaoTempoRepository previsaoTempoRepository;

    @Mock
    private EstacaoCadastro estacaoCadastro;

    @InjectMocks
    private FazendaService fazendaService;

    @Test
    void criarAssociaFazendaAoUsuarioInformado() {
        long usuarioId = 7L;
        Usuario usuario = new Usuario("Ana Silva", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        FazendaRequest request = new FazendaRequest("Fazenda Horizonte", "Goiás", 1200.5, null, null, null, null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(fazendaRepository.save(any(Fazenda.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FazendaResponse response = fazendaService.criar(usuarioId, request);

        ArgumentCaptor<Fazenda> fazendaCaptor = ArgumentCaptor.forClass(Fazenda.class);
        verify(fazendaRepository).save(fazendaCaptor.capture());
        assertSame(usuario, fazendaCaptor.getValue().getUsuario());
        assertEquals(request.nome(), response.nome());
        assertEquals(request.areaTotal(), response.areaTotal());
    }

    @Test
    void criarRejeitaUsuarioInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> fazendaService.criar(99L, new FazendaRequest("Fazenda", null, 10.0, null, null, null, null))
        );
        verifyNoInteractions(fazendaRepository);
    }

    @Test
    void excluirRejeitaFazendaComTalhoesVinculados() {
        Fazenda fazenda = new Fazenda(
                "Fazenda Horizonte",
                "Goiás",
                1200.5,
                null, null, null, null,
                new Usuario("Ana Silva", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR)
        );
        when(fazendaRepository.findById(4L)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.existsByFazenda_Id(4L)).thenReturn(true);

        assertThrows(ConflitoException.class, () -> fazendaService.excluir(4L));

        verify(fazendaRepository, never()).delete(any(Fazenda.class));
    }

    @Test
    void respostaIndicaFazendaDesativadaQuandoODonoEstaInativo() {
        Usuario dono = new Usuario("Ana Silva", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        dono.desativar();
        Fazenda fazenda = new Fazenda("Horizonte", "GO", 10.0, null, null, null, null, dono);
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));

        assertFalse(fazendaService.buscar(1L).ativa());
    }

    @Test
    void excluirRemoveAPrevisaoDerivadaJuntoComAFazenda() {
        Fazenda fazenda = new Fazenda("Horizonte", "GO", 10.0, null, null, null, null,
                new Usuario("Ana Silva", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR));
        when(fazendaRepository.findById(4L)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.existsByFazenda_Id(4L)).thenReturn(false);

        fazendaService.excluir(4L);

        verify(previsaoTempoRepository).deleteAllByFazenda_Id(4L);
        verify(estacaoCadastro).removerSeExistir(4L);
        verify(fazendaRepository).delete(fazenda);
    }
}
