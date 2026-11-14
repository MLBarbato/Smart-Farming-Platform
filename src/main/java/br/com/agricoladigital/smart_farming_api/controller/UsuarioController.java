package br.com.agricoladigital.smart_farming_api.controller;

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

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody CriarUsuarioRequest request) {
        UsuarioResponse response = usuarioService.criar(request);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + response.id())).body(response);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listar();
    }

    @PreAuthorize("@acesso.podeAcessarUsuario(authentication, #id)")
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return usuarioService.buscar(id);
    }

    @PreAuthorize("@acesso.podeAcessarUsuario(authentication, #id)")
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
    @PatchMapping("/{id}/senha")
    public ResponseEntity<Void> alterarSenha(
            @PathVariable Long id,
            @Valid @RequestBody AlterarSenhaRequest request
    ) {
        usuarioService.alterarSenha(id, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/desativar")
    public ResponseEntity<Void> desativarPropriaConta(Authentication authentication) {
        Long id = AcessoService.idDoUsuario(authentication);
        usuarioService.desativar(id, id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<Void> desativar(@PathVariable Long id, Authentication authentication) {
        usuarioService.desativar(id, AcessoService.idDoUsuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(@PathVariable Long id) {
        usuarioService.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
