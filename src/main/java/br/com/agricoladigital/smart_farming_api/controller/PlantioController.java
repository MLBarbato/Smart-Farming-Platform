package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

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

@Tag(name = "Plantios", description = "Plantios de um talhão: ciclo PLANEJADO, EM_CULTIVO, COLHIDO ou CANCELADO, e produtividade.")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/talhoes/{talhaoId}/plantios")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class PlantioController {
    private final PlantioService plantioService;

    public PlantioController(PlantioService plantioService) {
        this.plantioService = plantioService;
    }

    @Operation(summary = "Criar plantio", description = "Nasce PLANEJADO. Só um plantio EM_CULTIVO por talhão e sem períodos sobrepostos.")
    @Erro(codigo = "409", descricao = "O talhão já tem plantio em cultivo ou o período conflita com outro plantio")
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

    @Operation(summary = "Listar plantios do talhão", description = "Filtros opcionais: status e safra (AAAA/AAAA).")
    @GetMapping
    public List<PlantioResponse> listar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @RequestParam(required = false) StatusPlantio status,
            @RequestParam(required = false) String safra
    ) {
        return plantioService.listar(fazendaId, talhaoId, status, safra);
    }

    @Operation(summary = "Consultar plantio")
    @GetMapping("/{plantioId}")
    public PlantioResponse buscar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId
    ) {
        return plantioService.buscar(fazendaId, talhaoId, plantioId);
    }

    @Operation(summary = "Atualizar plantio")
    @Erro(codigo = "409", descricao = "Plantio encerrado (COLHIDO ou CANCELADO), troca de cultura fora do estado PLANEJADO ou período conflitante")
    @PutMapping("/{plantioId}")
    public PlantioResponse atualizar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId,
            @Valid @RequestBody PlantioRequest request
    ) {
        return plantioService.atualizar(fazendaId, talhaoId, plantioId, request);
    }

    @Operation(summary = "Mudar o estado do plantio", description = "PLANEJADO para EM_CULTIVO ou CANCELADO; EM_CULTIVO para COLHIDO (exige dataColheitaReal) ou CANCELADO. O sistema calcula a produtividade em kg/ha.")
    @Erro(codigo = "409", descricao = "Transição não permitida")
    @PatchMapping("/{plantioId}/status")
    public PlantioResponse alterarStatus(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId,
            @Valid @RequestBody AlterarStatusPlantioRequest request
    ) {
        return plantioService.alterarStatus(fazendaId, talhaoId, plantioId, request);
    }

    @Operation(summary = "Corrigir a colheita", description = "Corrige data e quantidade de um plantio já COLHIDO.")
    @Erro(codigo = "409", descricao = "O plantio não está COLHIDO")
    @PatchMapping("/{plantioId}/colheita")
    public PlantioResponse registrarColheita(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long plantioId,
            @Valid @RequestBody RegistrarColheitaRequest request
    ) {
        return plantioService.registrarColheita(fazendaId, talhaoId, plantioId, request);
    }

    @Operation(summary = "Excluir plantio", description = "Somente plantios PLANEJADOS.")
    @Erro(codigo = "409", descricao = "Somente plantios PLANEJADOS podem ser excluídos; use a mudança de status para cancelar")
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
