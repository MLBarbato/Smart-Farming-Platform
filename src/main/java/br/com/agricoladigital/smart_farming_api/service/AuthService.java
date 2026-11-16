package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.CriarUsuarioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.LoginRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.RegistroRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.TokenResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.UsuarioResponse;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ContaDesativadaException;
import br.com.agricoladigital.smart_farming_api.exception.CredenciaisInvalidasException;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import br.com.agricoladigital.smart_farming_api.security.LimiteTentativasLogin;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private static final String TIPO_TOKEN = "Bearer";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final LimiteTentativasLogin limiteTentativas;
    private final String hashFicticio;

    public AuthService(
            UsuarioRepository usuarioRepository,
            UsuarioService usuarioService,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            RefreshTokenService refreshTokenService,
            LimiteTentativasLogin limiteTentativas
    ) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.limiteTentativas = limiteTentativas;
        // Usado para gastar o mesmo tempo de verificação quando o e-mail não existe.
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-para-tempo-constante");
    }

    /** Cadastro público: o perfil nunca vem do cliente, sempre AGRICULTOR. */
    public UsuarioResponse registrar(RegistroRequest request) {
        return usuarioService.criar(new CriarUsuarioRequest(
                request.nome(), request.email(), request.senha(), PerfilUsuario.AGRICULTOR
        ));
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String ip) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        // O bloqueio é checado antes de validar a senha: durante o bloqueio nem a senha correta entra.
        limiteTentativas.verificar(email, ip);
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        String hash = usuario != null ? usuario.getSenhaHash() : hashFicticio;
        boolean senhaConfere = passwordEncoder.matches(request.senha(), hash);
        if (usuario == null || !senhaConfere) {
            limiteTentativas.registrarFalha(email, ip);
            throw new CredenciaisInvalidasException();
        }
        limiteTentativas.registrarSucesso(email, ip);
        // A situação da conta só é revelada depois de a senha ser validada.
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new ContaDesativadaException();
        }
        return emitirTokens(usuario);
    }

    @Transactional
    public TokenResponse renovar(String refreshToken) {
        Usuario usuario = refreshTokenService.consumir(refreshToken);
        return emitirTokens(usuario);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revogar(refreshToken);
    }

    private TokenResponse emitirTokens(Usuario usuario) {
        return new TokenResponse(
                tokenService.gerarAccessToken(usuario),
                refreshTokenService.emitir(usuario),
                TIPO_TOKEN,
                tokenService.expiracaoEmSegundos(),
                UsuarioService.paraResponse(usuario)
        );
    }
}
