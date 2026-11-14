package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.RefreshToken;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ContaDesativadaException;
import br.com.agricoladigital.smart_farming_api.exception.RefreshTokenInvalidoException;
import br.com.agricoladigital.smart_farming_api.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    @Mock private RefreshTokenRepository repository;

    private RefreshTokenService criar() {
        return new RefreshTokenService(repository, 7);
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        ReflectionTestUtils.setField(usuario, "id", 1L);
        return usuario;
    }

    @Test
    void emitirGravaApenasOHashDoToken() {
        String valor = criar().emitir(usuario());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertNotEquals(valor, captor.getValue().getTokenHash());
        assertEquals(RefreshTokenService.calcularHash(valor), captor.getValue().getTokenHash());
        assertTrue(captor.getValue().getExpiraEm().isAfter(LocalDateTime.now().plusDays(6)));
    }

    @Test
    void consumirRevogaOTokenEDevolveOUsuario() {
        Usuario usuario = usuario();
        RefreshToken token = new RefreshToken(usuario, "hash", LocalDateTime.now().plusDays(1));
        when(repository.findByTokenHash(RefreshTokenService.calcularHash("valor"))).thenReturn(Optional.of(token));

        Usuario resultado = criar().consumir("valor");

        assertSame(usuario, resultado);
        assertTrue(token.isRevogado());
    }

    @Test
    void consumirRejeitaTokenDesconhecido() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(RefreshTokenInvalidoException.class, () -> criar().consumir("valor"));
    }

    @Test
    void consumirRejeitaTokenExpirado() {
        RefreshToken token = new RefreshToken(usuario(), "hash", LocalDateTime.now().minusMinutes(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(RefreshTokenInvalidoException.class, () -> criar().consumir("valor"));
    }

    @Test
    void reutilizarTokenRevogadoRevogaTodasAsSessoesDoUsuario() {
        RefreshToken token = new RefreshToken(usuario(), "hash", LocalDateTime.now().plusDays(1));
        token.revogar();
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(RefreshTokenInvalidoException.class, () -> criar().consumir("valor"));

        verify(repository).revogarTodosDoUsuario(1L);
    }

    @Test
    void consumirRejeitaUsuarioDesativado() {
        Usuario usuario = usuario();
        usuario.desativar();
        RefreshToken token = new RefreshToken(usuario, "hash", LocalDateTime.now().plusDays(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(ContaDesativadaException.class, () -> criar().consumir("valor"));

        verify(repository).revogarTodosDoUsuario(1L);
    }
}
