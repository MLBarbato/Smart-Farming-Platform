package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.RegraAlertaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.RegraAlertaResponse;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.service.RegraAlertaService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Regras compartilhadas: qualquer usuário autenticado consulta; somente administradores ajustam. */
@Tag(name = "Regras de alerta", description = "Regras de seca, excesso de chuva, risco de geada e falha de sensor. Valores de demonstração.")
@RestController
@RequestMapping("/api/v1/regras-alerta")
public class RegraAlertaController {
    private final RegraAlertaService regraService;

    public RegraAlertaController(RegraAlertaService regraService) {
        this.regraService = regraService;
    }

    @Operation(summary = "Listar regras de alerta", description = "Filtros opcionais: evento e ativa. Qualquer usuário autenticado.")
    @GetMapping
    public List<RegraAlertaResponse> listar(
            @RequestParam(required = false) EventoAlerta evento,
            @RequestParam(required = false) Boolean ativa
    ) {
        return regraService.listar(evento, ativa);
    }

    @Operation(summary = "Consultar regra de alerta")
    @GetMapping("/{id}")
    public RegraAlertaResponse buscar(@PathVariable Long id) {
        return regraService.buscar(id);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Ajustar regra de alerta", description = "Somente administrador. O limiar deve estar na faixa da variável, a janela é obrigatória nos eventos com janela e proibida em FALHA_SENSOR, e a ordem de severidade deve ser mantida.")
    @PutMapping("/{id}")
    public RegraAlertaResponse atualizar(@PathVariable Long id, @Valid @RequestBody RegraAlertaRequest request) {
        return regraService.atualizar(id, request);
    }
}
