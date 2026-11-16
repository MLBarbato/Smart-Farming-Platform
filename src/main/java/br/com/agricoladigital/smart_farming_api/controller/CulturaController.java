package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.CulturaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.CulturaResponse;
import br.com.agricoladigital.smart_farming_api.service.CulturaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Catálogo compartilhado: qualquer usuário autenticado consulta; somente administradores alteram. */
@Tag(name = "Culturas", description = "Catálogo de culturas e parâmetros de irrigação. Consulta para todos; alteração só do administrador.")
@RestController
@RequestMapping("/api/v1/culturas")
public class CulturaController {
    private final CulturaService culturaService;

    public CulturaController(CulturaService culturaService) {
        this.culturaService = culturaService;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Criar cultura", description = "Somente administrador. O nome é único (sem diferenciar maiúsculas). Os parâmetros de irrigação são opcionais, mas devem vir juntos.")
    @Erro(codigo = "409", descricao = "Já existe uma cultura com este nome")
    @PostMapping
    public ResponseEntity<CulturaResponse> criar(@Valid @RequestBody CulturaRequest request) {
        CulturaResponse response = culturaService.criar(request);
        return ResponseEntity.created(URI.create("/api/v1/culturas/" + response.id())).body(response);
    }

    @Operation(summary = "Listar culturas", description = "Qualquer usuário autenticado, ordenadas por nome.")
    @GetMapping
    public List<CulturaResponse> listar() {
        return culturaService.listar();
    }

    @Operation(summary = "Consultar cultura")
    @GetMapping("/{id}")
    public CulturaResponse buscar(@PathVariable Long id) {
        return culturaService.buscar(id);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Atualizar cultura", description = "Somente administrador. O PUT substitui o cadastro: omitir os parâmetros de irrigação os limpa.")
    @Erro(codigo = "409", descricao = "Já existe uma cultura com este nome")
    @PutMapping("/{id}")
    public CulturaResponse atualizar(@PathVariable Long id, @Valid @RequestBody CulturaRequest request) {
        return culturaService.atualizar(id, request);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Excluir cultura", description = "Somente administrador.")
    @Erro(codigo = "409", descricao = "Cultura com plantios vinculados")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        culturaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
