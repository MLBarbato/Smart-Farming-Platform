package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarSenhaRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.AtualizarUsuarioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.CriarUsuarioRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.UsuarioResponse;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.SenhaAtualInvalidaException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void criarArmazenaSenhaCodificadaESemRetornaLaNaResposta() {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "Ana Silva", "ana@example.com", "senha-segura", PerfilUsuario.AGRICULTOR
        );
        when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.senha())).thenReturn("hash-bcrypt");
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponse response = usuarioService.criar(request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertEquals("hash-bcrypt", usuarioCaptor.getValue().getSenhaHash());
        assertEquals(request.email(), response.email());
        assertEquals("Ana Silva", response.nome());
        assertEquals(PerfilUsuario.AGRICULTOR, response.perfil());
    }

    @Test
    void criarRejeitaEmailJaCadastrado() {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "Ana Silva", "ana@example.com", "senha-segura", PerfilUsuario.AGRICULTOR
        );
        when(usuarioRepository.existsByEmail(request.email())).thenReturn(true);

        assertThrows(ConflitoException.class, () -> usuarioService.criar(request));
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void atualizarDadosNaoAlteraSenha() {
        Usuario usuario = new Usuario(
                "Ana Silva", "ana@example.com", "hash-original", PerfilUsuario.AGRICULTOR
        );
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        AtualizarUsuarioRequest request = new AtualizarUsuarioRequest(
                "Ana S.", "ana.nova@example.com", PerfilUsuario.AGRICULTOR
        );

        usuarioService.atualizar(1L, request, 1L, false);

        assertEquals("hash-original", usuario.getSenhaHash());
        assertEquals("ana.nova@example.com", usuario.getEmail());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void alterarSenhaConfereSenhaAtualEArmazenaNovoHash() {
        Usuario usuario = new Usuario(
                "Ana Silva", "ana@example.com", "hash-original", PerfilUsuario.AGRICULTOR
        );
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-atual", "hash-original")).thenReturn(true);
        when(passwordEncoder.encode("senha-nova")).thenReturn("hash-novo");

        usuarioService.alterarSenha(1L, new AlterarSenhaRequest("senha-atual", "senha-nova"));

        assertEquals("hash-novo", usuario.getSenhaHash());
        verify(passwordEncoder).matches("senha-atual", "hash-original");
    }

    @Test
    void alterarSenhaRejeitaSenhaAtualIncorreta() {
        Usuario usuario = new Usuario(
                "Ana Silva", "ana@example.com", "hash-original", PerfilUsuario.AGRICULTOR
        );
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-errada", "hash-original")).thenReturn(false);

        assertThrows(
                SenhaAtualInvalidaException.class,
                () -> usuarioService.alterarSenha(1L, new AlterarSenhaRequest("senha-errada", "senha-nova"))
        );

        assertEquals("hash-original", usuario.getSenhaHash());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void alterarSenhaRejeitaUsuarioInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> usuarioService.alterarSenha(99L, new AlterarSenhaRequest("senha-atual", "senha-nova"))
        );
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void desativarMarcaUsuarioComoInativoERevogaSessoes() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        usuarioService.desativar(1L, 1L);

        assertFalse(usuario.getAtivo());
        verify(refreshTokenService).revogarTodos(1L);
    }

    @Test
    void desativarRejeitaUsuarioJaInativo() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        usuario.desativar();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThrows(ConflitoException.class, () -> usuarioService.desativar(1L, 2L));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void administradorNaoPodeDesativarAPropriaConta() {
        Usuario admin = new Usuario("Admin", "admin@example.com", "hash", PerfilUsuario.ADMINISTRADOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(ConflitoException.class, () -> usuarioService.desativar(1L, 1L));

        assertTrue(admin.getAtivo());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void administradorPodeDesativarOutroUsuario() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.ADMINISTRADOR);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));

        usuarioService.desativar(2L, 1L);

        assertFalse(usuario.getAtivo());
    }

    @Test
    void ativarReativaUsuarioInativo() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        usuario.desativar();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        usuarioService.ativar(1L);

        assertTrue(usuario.getAtivo());
    }

    @Test
    void ativarRejeitaUsuarioJaAtivo() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThrows(ConflitoException.class, () -> usuarioService.ativar(1L));
    }

    @Test
    void agricultorNaoPodeAlterarOProprioPerfil() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        AtualizarUsuarioRequest request = new AtualizarUsuarioRequest(
                "Ana", "ana@example.com", PerfilUsuario.ADMINISTRADOR
        );

        assertThrows(AccessDeniedException.class, () -> usuarioService.atualizar(1L, request, 1L, false));

        assertEquals(PerfilUsuario.AGRICULTOR, usuario.getPerfil());
    }

    @Test
    void administradorNaoPodeAlterarOProprioPerfil() {
        Usuario admin = new Usuario("Admin", "admin@example.com", "hash", PerfilUsuario.ADMINISTRADOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        AtualizarUsuarioRequest request = new AtualizarUsuarioRequest(
                "Admin", "admin@example.com", PerfilUsuario.AGRICULTOR
        );

        assertThrows(ConflitoException.class, () -> usuarioService.atualizar(1L, request, 1L, true));
    }

    @Test
    void criarNormalizaEmailParaMinusculas() {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "Ana Silva", "  Ana@Example.COM ", "senha-segura", PerfilUsuario.AGRICULTOR
        );
        when(usuarioRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode(request.senha())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponse response = usuarioService.criar(request);

        assertEquals("ana@example.com", response.email());
    }
}
