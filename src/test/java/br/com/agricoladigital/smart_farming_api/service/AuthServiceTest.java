package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.LoginRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.RegistroRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.CriarUsuarioRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.TokenResponse;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ContaDesativadaException;
import br.com.agricoladigital.smart_farming_api.exception.CredenciaisInvalidasException;
import br.com.agricoladigital.smart_farming_api.exception.LoginBloqueadoException;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import br.com.agricoladigital.smart_farming_api.security.LimiteTentativasLogin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioService usuarioService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private RefreshTokenService refreshTokenService;

    private static final String IP = "203.0.113.7";

    private LimiteTentativasLogin limite;
    private AuthService authService;

    @BeforeEach
    void criarServico() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash-ficticio");
        limite = new LimiteTentativasLogin(3, 20, Duration.ofMinutes(15), Duration.ofMinutes(15), Clock.systemUTC());
        authService = new AuthService(
                usuarioRepository, usuarioService, passwordEncoder, tokenService, refreshTokenService, limite
        );
    }

    @Test
    void registroSempreCriaAgricultor() {
        authService.registrar(new RegistroRequest("Ana", "ana@example.com", "senha-segura"));

        ArgumentCaptor<CriarUsuarioRequest> captor = ArgumentCaptor.forClass(CriarUsuarioRequest.class);
        verify(usuarioService).criar(captor.capture());
        assertEquals(PerfilUsuario.AGRICULTOR, captor.getValue().perfil());
    }

    @Test
    void loginValidoEmiteAccessEPrefreshToken() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-segura", "hash")).thenReturn(true);
        when(tokenService.gerarAccessToken(usuario)).thenReturn("access");
        when(tokenService.expiracaoEmSegundos()).thenReturn(900L);
        when(refreshTokenService.emitir(usuario)).thenReturn("refresh");

        TokenResponse response = authService.login(new LoginRequest(" Ana@Example.com ", "senha-segura"), IP);

        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertEquals("Bearer", response.tipo());
        assertEquals(900L, response.expiraEmSegundos());
        assertEquals("Ana", response.usuario().nome());
        assertEquals("ana@example.com", response.usuario().email());
        assertEquals(PerfilUsuario.AGRICULTOR, response.usuario().perfil());
    }

    @Test
    void loginRejeitaSenhaIncorreta() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThrows(CredenciaisInvalidasException.class,
                () -> authService.login(new LoginRequest("ana@example.com", "errada"), IP));
        verifyNoInteractions(tokenService, refreshTokenService);
    }

    @Test
    void loginRejeitaEmailInexistenteSemRevelarQueNaoExiste() {
        when(usuarioRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("qualquer", "hash-ficticio")).thenReturn(false);

        assertThrows(CredenciaisInvalidasException.class,
                () -> authService.login(new LoginRequest("x@example.com", "qualquer"), IP));
        verify(passwordEncoder).matches("qualquer", "hash-ficticio");
    }

    @Test
    void contaDesativadaSoERevelaDepoisDeValidarASenha() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        usuario.desativar();
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);
        when(passwordEncoder.matches("certa", "hash")).thenReturn(true);

        assertThrows(CredenciaisInvalidasException.class,
                () -> authService.login(new LoginRequest("ana@example.com", "errada"), IP));
        assertThrows(ContaDesativadaException.class,
                () -> authService.login(new LoginRequest("ana@example.com", "certa"), IP));
        verifyNoInteractions(tokenService, refreshTokenService);
    }

    @Test
    void renovarEmiteNovosTokensParaOUsuarioDoRefreshConsumido() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        when(refreshTokenService.consumir("antigo")).thenReturn(usuario);
        when(tokenService.gerarAccessToken(usuario)).thenReturn("novo-access");
        when(refreshTokenService.emitir(usuario)).thenReturn("novo-refresh");

        TokenResponse response = authService.renovar("antigo");

        assertEquals("novo-access", response.accessToken());
        assertEquals("novo-refresh", response.refreshToken());
        assertEquals("ana@example.com", response.usuario().email());
        verify(refreshTokenService, times(1)).consumir(any());
    }

    @Test
    void loginBloqueiaAposMuitasFalhasMesmoComSenhaCorreta() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);
        LoginRequest errado = new LoginRequest("ana@example.com", "errada");

        for (int i = 0; i < 3; i++) {
            assertThrows(CredenciaisInvalidasException.class, () -> authService.login(errado, IP));
        }

        assertThrows(LoginBloqueadoException.class,
                () -> authService.login(new LoginRequest("ana@example.com", "certa"), IP));
        verify(passwordEncoder, never()).matches(eq("certa"), anyString());
        verifyNoInteractions(tokenService, refreshTokenService);
    }

    @Test
    void loginBemSucedidoZeraOContadorDeFalhasDaConta() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);
        when(passwordEncoder.matches("certa", "hash")).thenReturn(true);
        LoginRequest errado = new LoginRequest("ana@example.com", "errada");
        LoginRequest certo = new LoginRequest("ana@example.com", "certa");

        for (int rodada = 0; rodada < 3; rodada++) {
            assertThrows(CredenciaisInvalidasException.class, () -> authService.login(errado, IP));
            assertThrows(CredenciaisInvalidasException.class, () -> authService.login(errado, IP));
            assertDoesNotThrow(() -> authService.login(certo, IP));
        }
    }

    @Test
    void falhasComEmailInexistenteTambemContam() {
        when(usuarioRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("qualquer", "hash-ficticio")).thenReturn(false);
        LoginRequest request = new LoginRequest("x@example.com", "qualquer");

        for (int i = 0; i < 3; i++) {
            assertThrows(CredenciaisInvalidasException.class, () -> authService.login(request, IP));
        }
        assertThrows(LoginBloqueadoException.class, () -> authService.login(request, IP));
    }
}
