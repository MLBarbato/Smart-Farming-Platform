package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.request.FazendaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.FazendaResponse;
import br.com.agricoladigital.smart_farming_api.service.FazendaService;
import jakarta.validation.Valid;
import br.com.agricoladigital.smart_farming_api.security.AcessoService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FazendaController {
    private final FazendaService fazendaService;

    public FazendaController(FazendaService fazendaService) {
        this.fazendaService = fazendaService;
    }

    @PreAuthorize("@acesso.podeAcessarUsuario(authentication, #usuarioId)")
    @PostMapping("/usuarios/{usuarioId}/fazendas")
    public ResponseEntity<FazendaResponse> criar(
            @PathVariable Long usuarioId,
            @Valid @RequestBody FazendaRequest request
    ) {
        FazendaResponse response = fazendaService.criar(usuarioId, request);
        return ResponseEntity.created(URI.create("/api/v1/fazendas/" + response.id())).body(response);
    }

    @PreAuthorize("@acesso.podeAcessarUsuario(authentication, #usuarioId)")
    @GetMapping("/usuarios/{usuarioId}/fazendas")
    public List<FazendaResponse> listarPorUsuario(@PathVariable Long usuarioId) {
        return fazendaService.listarPorUsuario(usuarioId);
    }

    @GetMapping("/fazendas")
    public List<FazendaResponse> listar(Authentication authentication) {
        if (AcessoService.ehAdministrador(authentication)) {
            return fazendaService.listar();
        }
        return fazendaService.listarPorUsuario(AcessoService.idDoUsuario(authentication));
    }

    @PreAuthorize("@acesso.podeAcessarFazenda(authentication, #id)")
    @GetMapping("/fazendas/{id}")
    public FazendaResponse buscar(@PathVariable Long id) {
        return fazendaService.buscar(id);
    }

    @PreAuthorize("@acesso.podeAcessarFazenda(authentication, #id)")
    @PutMapping("/fazendas/{id}")
    public FazendaResponse atualizar(@PathVariable Long id, @Valid @RequestBody FazendaRequest request) {
        return fazendaService.atualizar(id, request);
    }

    @PreAuthorize("@acesso.podeAcessarFazenda(authentication, #id)")
    @DeleteMapping("/fazendas/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        fazendaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
