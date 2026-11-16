package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.request.RegraAlertaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.RegraAlertaResponse;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.service.RegraAlertaService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Regras compartilhadas: qualquer usuário autenticado consulta; somente administradores ajustam. */
@RestController
@RequestMapping("/api/v1/regras-alerta")
public class RegraAlertaController {
    private final RegraAlertaService regraService;

    public RegraAlertaController(RegraAlertaService regraService) {
        this.regraService = regraService;
    }

    @GetMapping
    public List<RegraAlertaResponse> listar(
            @RequestParam(required = false) EventoAlerta evento,
            @RequestParam(required = false) Boolean ativa
    ) {
        return regraService.listar(evento, ativa);
    }

    @GetMapping("/{id}")
    public RegraAlertaResponse buscar(@PathVariable Long id) {
        return regraService.buscar(id);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/{id}")
    public RegraAlertaResponse atualizar(@PathVariable Long id, @Valid @RequestBody RegraAlertaRequest request) {
        return regraService.atualizar(id, request);
    }
}
