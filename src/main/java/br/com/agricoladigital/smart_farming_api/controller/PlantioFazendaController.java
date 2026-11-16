package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.response.PlantioResponse;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.service.PlantioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Plantios de todos os talhões de uma fazenda (as rotas por talhão ficam em {@link PlantioController}). */
@Tag(name = "Plantios", description = "Plantios de um talhão: ciclo PLANEJADO, EM_CULTIVO, COLHIDO ou CANCELADO, e produtividade.")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/plantios")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class PlantioFazendaController {
    private final PlantioService plantioService;

    public PlantioFazendaController(PlantioService plantioService) {
        this.plantioService = plantioService;
    }

    @Operation(summary = "Listar plantios da fazenda",
            description = "Plantios de todos os talhões da fazenda, dos mais recentes aos mais antigos. Filtros opcionais: talhaoId, culturaId, status e safra (AAAA/AAAA).")
    @GetMapping
    public List<PlantioResponse> listar(
            @PathVariable Long fazendaId,
            @RequestParam(required = false) Long talhaoId,
            @RequestParam(required = false) Long culturaId,
            @RequestParam(required = false) StatusPlantio status,
            @RequestParam(required = false) String safra
    ) {
        return plantioService.listarDaFazenda(fazendaId, talhaoId, culturaId, status, safra);
    }
}
