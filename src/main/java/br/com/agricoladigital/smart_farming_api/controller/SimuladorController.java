package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.CenarioSimulacaoRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.CenarioSimulacaoResponse;
import br.com.agricoladigital.smart_farming_api.service.CenarioSimulacaoService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Cenários de demonstração do simulador. Só existe com o simulador ligado; só o administrador usa. */
@Tag(name = "Simulador", description = "Cenários de demonstração do simulador de sensores. Só existe com SIMULADOR_ENABLED=true; só administrador.")
@RestController
@RequestMapping("/api/v1/simulador/cenario")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@ConditionalOnProperty(name = "app.simulador.enabled", havingValue = "true")
public class SimuladorController {
    private final CenarioSimulacaoService cenarioService;

    public SimuladorController(CenarioSimulacaoService cenarioService) {
        this.cenarioService = cenarioService;
    }

    @Operation(summary = "Cenário vigente", description = "Cenário padrão e exceções por fazenda.")
    @GetMapping
    public CenarioSimulacaoResponse.Estado estado() {
        return cenarioService.estadoAtual();
    }

    @Operation(summary = "Trocar o cenário", description = "NORMAL, SECA, GEADA ou FALHA_SENSOR. Avalia os alertas das fazendas afetadas na hora.")
    @Erro(codigo = "404", descricao = "Fazenda não encontrada")
    @PostMapping
    public CenarioSimulacaoResponse trocar(@Valid @RequestBody CenarioSimulacaoRequest request) {
        return cenarioService.aplicar(request);
    }
}
