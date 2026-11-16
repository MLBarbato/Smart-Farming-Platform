package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.LoginRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.RefreshTokenRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.RegistroRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.TokenResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.UsuarioResponse;
import br.com.agricoladigital.smart_farming_api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "Autenticação", description = "Cadastro público, login, renovação e encerramento de sessão. Rotas sem token.")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Cadastrar agricultor", description = "Cria um usuário com perfil AGRICULTOR (o perfil não pode ser escolhido). O e-mail é único.")
    @Erro(codigo = "400", descricao = "Dados inválidos")
    @Erro(codigo = "409", descricao = "E-mail já cadastrado")
    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        UsuarioResponse response = authService.registrar(request);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + response.id())).body(response);
    }

    @Operation(summary = "Entrar", description = "Valida as credenciais e devolve o par de tokens. Após muitas tentativas erradas por e-mail e IP o acesso é bloqueado temporariamente (429, cabeçalho Retry-After).")
    @Erro(codigo = "400", descricao = "Dados inválidos")
    @Erro(codigo = "401", descricao = "Credenciais inválidas")
    @Erro(codigo = "403", descricao = "Conta desativada (codigo USUARIO_DESATIVADO)")
    @Erro(codigo = "429", descricao = "Login bloqueado temporariamente (codigo LOGIN_BLOQUEADO)")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    @Operation(summary = "Renovar tokens", description = "Troca um refresh token válido por um novo par; o refresh token anterior deixa de valer (rotação).")
    @Erro(codigo = "400", descricao = "Dados inválidos")
    @Erro(codigo = "401", descricao = "Refresh token inválido, expirado ou já usado")
    @PostMapping("/refresh")
    public TokenResponse renovar(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.renovar(request.refreshToken());
    }

    @Operation(summary = "Encerrar sessão", description = "Revoga o refresh token informado.")
    @Erro(codigo = "400", descricao = "Dados inválidos")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
