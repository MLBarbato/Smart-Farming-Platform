package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

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

@Tag(name = "Fazendas", description = "Fazendas do agricultor. O agricultor só acessa as próprias; o administrador acessa todas.")
@RestController
@RequestMapping("/api/v1")
public class FazendaController {
    private final FazendaService fazendaService;

    public FazendaController(FazendaService fazendaService) {
        this.fazendaService = fazendaService;
    }

    @PreAuthorize("@acesso.podeAcessarUsuario(authentication, #usuarioId)")
    @Operation(summary = "Criar fazenda para um usuário", description = "latitude e longitude devem vir juntas; habilitam previsão, estação virtual e mapa.")
    @PostMapping("/usuarios/{usuarioId}/fazendas")
    public ResponseEntity<FazendaResponse> criar(
            @PathVariable Long usuarioId,
            @Valid @RequestBody FazendaRequest request
    ) {
        FazendaResponse response = fazendaService.criar(usuarioId, request);
        return ResponseEntity.created(URI.create("/api/v1/fazendas/" + response.id())).body(response);
    }

    @PreAuthorize("@acesso.podeAcessarUsuario(authentication, #usuarioId)")
    @Operation(summary = "Listar fazendas de um usuário", description = "O próprio usuário ou o administrador.")
    @GetMapping("/usuarios/{usuarioId}/fazendas")
    public List<FazendaResponse> listarPorUsuario(@PathVariable Long usuarioId) {
        return fazendaService.listarPorUsuario(usuarioId);
    }

    @Operation(summary = "Listar minhas fazendas", description = "Agricultor: as próprias fazendas; administrador: todas.")
    @GetMapping("/fazendas")
    public List<FazendaResponse> listar(Authentication authentication) {
        if (AcessoService.ehAdministrador(authentication)) {
            return fazendaService.listar();
        }
        return fazendaService.listarPorUsuario(AcessoService.idDoUsuario(authentication));
    }

    @PreAuthorize("@acesso.podeAcessarFazenda(authentication, #id)")
    @Operation(summary = "Consultar fazenda")
    @GetMapping("/fazendas/{id}")
    public FazendaResponse buscar(@PathVariable Long id) {
        return fazendaService.buscar(id);
    }

    @PreAuthorize("@acesso.podeAcessarFazenda(authentication, #id)")
    @Operation(summary = "Atualizar fazenda")
    @PutMapping("/fazendas/{id}")
    public FazendaResponse atualizar(@PathVariable Long id, @Valid @RequestBody FazendaRequest request) {
        return fazendaService.atualizar(id, request);
    }

    @PreAuthorize("@acesso.podeAcessarFazenda(authentication, #id)")
    @Operation(summary = "Excluir fazenda", description = "Remove também a previsão e a estação virtual. Fazenda com talhões não pode ser excluída.")
    @Erro(codigo = "409", descricao = "Fazenda com talhões vinculados")
    @DeleteMapping("/fazendas/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        fazendaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
