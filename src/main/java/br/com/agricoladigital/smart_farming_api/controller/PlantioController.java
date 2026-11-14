package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusPlantioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.PlantioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.RegistrarColheitaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.PlantioResponse;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.service.PlantioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/talhoes/{talhaoId}/plantios")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class PlantioController {
    private final PlantioService plantioService;

    public PlantioController(PlantioService plantioService) {
        this.plantioService = plantioService;
    }

    @PostMapping
    public ResponseEntity<PlantioResponse> criar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @Valid @RequestBody PlantioRequest request
    ) {
        PlantioResponse response = plantioService.criar(fazendaId, talhaoId, request);
        return ResponseEntity.created(URI.create(
                "/api/v1/fazendas/" + fazendaId + "/talhoes/" + talhaoId + "/plantios/" + response.id()
        )).body(response);
    }

    @GetMapping
    public List<PlantioResponse> listar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @RequestParam(required = false) StatusPlantio status,
            @RequestParam(required = false) String safra
    ) {
        return plantioService.listar(fazendaId, talhaoId, status, safra);
    }

    @GetMapping("/{plantioId}")
    public PlantioResponse buscar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId
    ) {
        return plantioService.buscar(fazendaId, talhaoId, plantioId);
    }

    @PutMapping("/{plantioId}")
    public PlantioResponse atualizar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId,
            @Valid @RequestBody PlantioRequest request
    ) {
        return plantioService.atualizar(fazendaId, talhaoId, plantioId, request);
    }

    @PatchMapping("/{plantioId}/status")
    public PlantioResponse alterarStatus(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId,
            @Valid @RequestBody AlterarStatusPlantioRequest request
    ) {
        return plantioService.alterarStatus(fazendaId, talhaoId, plantioId, request);
    }

    @PatchMapping("/{plantioId}/colheita")
    public PlantioResponse registrarColheita(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId,
            @Valid @RequestBody RegistrarColheitaRequest request
    ) {
        return plantioService.registrarColheita(fazendaId, talhaoId, plantioId, request);
    }

    @DeleteMapping("/{plantioId}")
    public ResponseEntity<Void> excluir(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId
    ) {
        plantioService.excluir(fazendaId, talhaoId, plantioId);
        return ResponseEntity.noContent().build();
    }
}
