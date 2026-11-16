package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusEstacaoRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.EstacaoResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.LeituraAgregadaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.entity.IntervaloAgregacao;
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

@Tag(name = "Estação virtual", description = "Estação meteorológica virtual da fazenda, alimentada por leituras horárias do Open-Meteo.")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/estacao")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class EstacaoController {
    private final EstacaoService estacaoService;

    public EstacaoController(EstacaoService estacaoService) {
        this.estacaoService = estacaoService;
    }

    /** Cria a estação virtual da fazenda (exige latitude e longitude) e carrega o histórico recente. */
    @Operation(summary = "Criar estação virtual", description = "Uma estação por fazenda; exige latitude e longitude. Carrega o histórico recente do Open-Meteo.")
    @Erro(codigo = "400", descricao = "Fazenda sem latitude e longitude")
    @Erro(codigo = "409", descricao = "A fazenda já tem estação")
    @Erro(codigo = "503", descricao = "Provedor indisponível")
    @PostMapping
    public ResponseEntity<EstacaoResponse> criar(@PathVariable Long fazendaId) {
        EstacaoResponse response = estacaoService.criar(fazendaId);
        return ResponseEntity.created(URI.create("/api/v1/fazendas/" + fazendaId + "/estacao")).body(response);
    }

    @Operation(summary = "Consultar estação", description = "Situação (ATUAL, DESATUALIZADA, SEM_SINCRONIZACAO, INATIVA) e últimas leituras. Exiba a atribuição.")
    @GetMapping
    public EstacaoResponse consultar(@PathVariable Long fazendaId) {
        return estacaoService.consultar(fazendaId);
    }

    @Operation(summary = "Mudar o estado da estação", description = "ATIVO, INATIVO ou MANUTENCAO. Estação inativa não é sincronizada.")
    @PatchMapping("/status")
    public EstacaoResponse alterarStatus(
            @PathVariable Long fazendaId,
            @Valid @RequestBody AlterarStatusEstacaoRequest request
    ) {
        return estacaoService.alterarStatus(fazendaId, request.status());
    }

    /** Força a sincronização com o provedor (limitada por um intervalo mínimo). */
    @Operation(summary = "Sincronizar agora", description = "Busca as leituras horárias novas no provedor (com intervalo mínimo entre chamadas).")
    @Erro(codigo = "400", descricao = "Estação inativa")
    @Erro(codigo = "503", descricao = "Provedor indisponível")
    @PostMapping("/sincronizar")
    public EstacaoResponse sincronizar(@PathVariable Long fazendaId) {
        return estacaoService.sincronizarManualmente(fazendaId);
    }

    /** Histórico de uma variável; {@code de} e {@code ate} em ISO-8601 com fuso (padrão: últimas 24 h). */
    @Operation(summary = "Leituras agregadas da estação", description = "Mínimo, média, máximo e quantidade por HORA (padrão; últimos 7 dias) ou DIA (últimos 30 dias) de uma variável. Filtros: variavel, de, ate, intervalo e fuso (IANA; padrão UTC). No máximo 1000 intervalos.")
    @GetMapping("/leituras/agregadas")
    public LeituraAgregadaResponse agregadas(
            @PathVariable Long fazendaId,
            @RequestParam VariavelMonitorada variavel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(required = false) IntervaloAgregacao intervalo,
            @RequestParam(required = false) String fuso
    ) {
        return estacaoService.agregadas(fazendaId, variavel, de, ate, intervalo, fuso);
    }

    @Operation(summary = "Histórico da estação", description = "Filtros: variavel, de, ate e limite (1 a 5000; padrão 500).")
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
    @Operation(summary = "Remover estação", description = "Apaga a estação e suas leituras.")
    @DeleteMapping
    public ResponseEntity<Void> remover(@PathVariable Long fazendaId) {
        estacaoService.remover(fazendaId);
        return ResponseEntity.noContent().build();
    }
}
