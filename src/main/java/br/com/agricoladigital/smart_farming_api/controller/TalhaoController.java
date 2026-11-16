package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.TalhaoRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.TalhaoResponse;
import br.com.agricoladigital.smart_farming_api.service.TalhaoService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "Talhões", description = "Áreas de cultivo de uma fazenda, com geometria GeoJSON opcional.")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/talhoes")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class TalhaoController {
    private final TalhaoService talhaoService;

    public TalhaoController(TalhaoService talhaoService) {
        this.talhaoService = talhaoService;
    }

    @Operation(summary = "Criar talhão", description = "geometria é um GeoJSON Polygon (coordenadas [longitude, latitude], anel fechado, até 1000 posições).")
    @PostMapping
    public ResponseEntity<TalhaoResponse> criar(
            @PathVariable Long fazendaId,
            @Valid @RequestBody TalhaoRequest request
    ) {
        TalhaoResponse response = talhaoService.criar(fazendaId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/fazendas/" + fazendaId + "/talhoes/" + response.id())
        ).body(response);
    }

    @Operation(summary = "Listar talhões da fazenda")
    @GetMapping
    public List<TalhaoResponse> listar(@PathVariable Long fazendaId) {
        return talhaoService.listarPorFazenda(fazendaId);
    }

    @Operation(summary = "Consultar talhão")
    @GetMapping("/{talhaoId}")
    public TalhaoResponse buscar(@PathVariable Long fazendaId, @PathVariable Long talhaoId) {
        return talhaoService.buscar(fazendaId, talhaoId);
    }

    @Operation(summary = "Atualizar talhão")
    @PutMapping("/{talhaoId}")
    public TalhaoResponse atualizar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @Valid @RequestBody TalhaoRequest request
    ) {
        return talhaoService.atualizar(fazendaId, talhaoId, request);
    }

    @Operation(summary = "Excluir talhão", description = "Talhão com plantios, dispositivos, alertas ou recomendações não pode ser excluído.")
    @Erro(codigo = "409", descricao = "Talhão com vínculos")
    @DeleteMapping("/{talhaoId}")
    public ResponseEntity<Void> excluir(@PathVariable Long fazendaId, @PathVariable Long talhaoId) {
        talhaoService.excluir(fazendaId, talhaoId);
        return ResponseEntity.noContent().build();
    }
}
