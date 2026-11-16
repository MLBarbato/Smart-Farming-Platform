package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.request.CenarioSimulacaoRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.CenarioSimulacaoResponse;
import br.com.agricoladigital.smart_farming_api.service.CenarioSimulacaoService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Cenários de demonstração do simulador. Só existe com o simulador ligado; só o administrador usa. */
@RestController
@RequestMapping("/api/v1/simulador/cenario")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@ConditionalOnProperty(name = "app.simulador.enabled", havingValue = "true")
public class SimuladorController {
    private final CenarioSimulacaoService cenarioService;

    public SimuladorController(CenarioSimulacaoService cenarioService) {
        this.cenarioService = cenarioService;
    }

    @GetMapping
    public CenarioSimulacaoResponse.Estado estado() {
        return cenarioService.estadoAtual();
    }

    @PostMapping
    public CenarioSimulacaoResponse trocar(@Valid @RequestBody CenarioSimulacaoRequest request) {
        return cenarioService.aplicar(request);
    }
}
