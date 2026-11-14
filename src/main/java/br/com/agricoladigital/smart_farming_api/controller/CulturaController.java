package br.com.agricoladigital.smart_farming_api.controller;

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
@RestController
@RequestMapping("/api/v1/culturas")
public class CulturaController {
    private final CulturaService culturaService;

    public CulturaController(CulturaService culturaService) {
        this.culturaService = culturaService;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<CulturaResponse> criar(@Valid @RequestBody CulturaRequest request) {
        CulturaResponse response = culturaService.criar(request);
        return ResponseEntity.created(URI.create("/api/v1/culturas/" + response.id())).body(response);
    }

    @GetMapping
    public List<CulturaResponse> listar() {
        return culturaService.listar();
    }

    @GetMapping("/{id}")
    public CulturaResponse buscar(@PathVariable Long id) {
        return culturaService.buscar(id);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/{id}")
    public CulturaResponse atualizar(@PathVariable Long id, @Valid @RequestBody CulturaRequest request) {
        return culturaService.atualizar(id, request);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        culturaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
