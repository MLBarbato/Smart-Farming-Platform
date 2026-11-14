package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.RefreshToken;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ContaDesativadaException;
import br.com.agricoladigital.smart_farming_api.exception.RefreshTokenInvalidoException;
import br.com.agricoladigital.smart_farming_api.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens opacos: o valor entregue ao cliente nunca é gravado, apenas seu hash SHA-256.
 * Cada uso rotaciona o token; reutilizar um token já revogado revoga todas as sessões do usuário.
 */
@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long validadeDias;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration-days}") long validadeDias
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.validadeDias = validadeDias;
    }

    @Transactional
    public String emitir(Usuario usuario) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshToken(
                usuario, calcularHash(valor), LocalDateTime.now().plusDays(validadeDias)
        ));
        return valor;
    }

    /** Valida e consome o token informado, devolvendo o usuário dono dele. */
    @Transactional(noRollbackFor = {RefreshTokenInvalidoException.class, ContaDesativadaException.class})
    public Usuario consumir(String valor) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(calcularHash(valor))
                .orElseThrow(RefreshTokenInvalidoException::new);
        Usuario usuario = token.getUsuario();

        if (token.isRevogado()) {
            refreshTokenRepository.revogarTodosDoUsuario(usuario.getId());
            throw new RefreshTokenInvalidoException();
        }
        token.revogar();
        if (token.estaExpirado(LocalDateTime.now())) {
            throw new RefreshTokenInvalidoException();
        }
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            refreshTokenRepository.revogarTodosDoUsuario(usuario.getId());
            throw new ContaDesativadaException();
        }
        return usuario;
    }

    @Transactional
    public void revogar(String valor) {
        refreshTokenRepository.findByTokenHash(calcularHash(valor)).ifPresent(RefreshToken::revogar);
    }

    @Transactional
    public void revogarTodos(Long usuarioId) {
        refreshTokenRepository.revogarTodosDoUsuario(usuarioId);
    }

    static String calcularHash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }
}
