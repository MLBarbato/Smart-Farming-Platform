package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarSenhaRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.AtualizarUsuarioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.CriarUsuarioRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.UsuarioResponse;
import br.com.agricoladigital.smart_farming_api.service.UsuarioService;
import jakarta.validation.Valid;
import br.com.agricoladigital.smart_farming_api.security.AcessoService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "Usuários", description = "Administração de usuários. Usuários nunca são excluídos, apenas desativados.")
@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Criar usuário", description = "Administrador cria usuário com o perfil escolhido.")
    @Erro(codigo = "409", descricao = "E-mail já cadastrado")
    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody CriarUsuarioRequest request) {
        UsuarioResponse response = usuarioService.criar(request);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + response.id())).body(response);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar usuários", description = "Somente administrador.")
    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listar();
    }

    @Operation(summary = "Consultar o usuário autenticado", description = "Dados do usuário dono do token (id, nome, e-mail, perfil).")
    @GetMapping("/me")
    public UsuarioResponse eu(Authentication authentication) {
        return usuarioService.buscar(AcessoService.idDoUsuario(authentication));
    }

    @PreAuthorize("@acesso.podeAcessarUsuario(authentication, #id)")
    @Operation(summary = "Consultar usuário", description = "O próprio usuário ou o administrador.")
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return usuarioService.buscar(id);
    }

    @PreAuthorize("@acesso.podeAcessarUsuario(authentication, #id)")
    @Operation(summary = "Atualizar usuário", description = "O próprio usuário ou o administrador; só o administrador altera o perfil (e ninguém altera o próprio).")
    @Erro(codigo = "409", descricao = "E-mail já cadastrado ou tentativa de alterar o próprio perfil")
    @PutMapping("/{id}")
    public UsuarioResponse atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarUsuarioRequest request,
            Authentication authentication
    ) {
        return usuarioService.atualizar(
                id,
                request,
                AcessoService.idDoUsuario(authentication),
                AcessoService.ehAdministrador(authentication)
        );
    }

    @PreAuthorize("@acesso.ehProprioUsuario(authentication, #id)")
    @Operation(summary = "Alterar a própria senha", description = "Exige a senha atual. Somente o próprio usuário.")
    @PatchMapping("/{id}/senha")
    public ResponseEntity<Void> alterarSenha(
            @PathVariable Long id,
            @Valid @RequestBody AlterarSenhaRequest request
    ) {
        usuarioService.alterarSenha(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desativar a própria conta", description = "Administradores não podem se desativar. O acesso é bloqueado nas próximas requisições.")
    @Erro(codigo = "409", descricao = "Administradores não podem desativar a própria conta, ou a conta já está desativada")
    @PatchMapping("/me/desativar")
    public ResponseEntity<Void> desativarPropriaConta(Authentication authentication) {
        Long id = AcessoService.idDoUsuario(authentication);
        usuarioService.desativar(id, id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Desativar usuário", description = "Somente administrador. Usuários nunca são excluídos.")
    @Erro(codigo = "409", descricao = "Usuário já desativado ou administrador tentando desativar a própria conta")
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<Void> desativar(@PathVariable Long id, Authentication authentication) {
        usuarioService.desativar(id, AcessoService.idDoUsuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Reativar usuário", description = "Somente administrador.")
    @Erro(codigo = "409", descricao = "O usuário já está ativo")
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(@PathVariable Long id) {
        usuarioService.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
