package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusEstacaoRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.EstacaoResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.service.EstacaoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/estacao")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class EstacaoController {
    private final EstacaoService estacaoService;

    public EstacaoController(EstacaoService estacaoService) {
        this.estacaoService = estacaoService;
    }

    /** Cria a estação virtual da fazenda (exige latitude e longitude) e carrega o histórico recente. */
    @PostMapping
    public ResponseEntity<EstacaoResponse> criar(@PathVariable Long fazendaId) {
        EstacaoResponse response = estacaoService.criar(fazendaId);
        return ResponseEntity.created(URI.create("/api/v1/fazendas/" + fazendaId + "/estacao")).body(response);
    }

    @GetMapping
    public EstacaoResponse consultar(@PathVariable Long fazendaId) {
        return estacaoService.consultar(fazendaId);
    }

    @PatchMapping("/status")
    public EstacaoResponse alterarStatus(
            @PathVariable Long fazendaId,
            @Valid @RequestBody AlterarStatusEstacaoRequest request
    ) {
        return estacaoService.alterarStatus(fazendaId, request.status());
    }

    /** Força a sincronização com o provedor (limitada por um intervalo mínimo). */
    @PostMapping("/sincronizar")
    public EstacaoResponse sincronizar(@PathVariable Long fazendaId) {
        return estacaoService.sincronizarManualmente(fazendaId);
    }

    /** Histórico de uma variável; {@code de} e {@code ate} em ISO-8601 com fuso (padrão: últimas 24 h). */
    @GetMapping("/leituras")
    public List<LeituraResponse> historico(
            @PathVariable Long fazendaId,
            @RequestParam VariavelMonitorada variavel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(required = false) Integer limite
    ) {
        return estacaoService.historico(fazendaId, variavel, de, ate, limite);
    }

    /** Remove a estação virtual com seus sensores e leituras (podem ser obtidos de novo recriando-a). */
    @DeleteMapping
    public ResponseEntity<Void> remover(@PathVariable Long fazendaId) {
        estacaoService.remover(fazendaId);
        return ResponseEntity.noContent().build();
    }
}
