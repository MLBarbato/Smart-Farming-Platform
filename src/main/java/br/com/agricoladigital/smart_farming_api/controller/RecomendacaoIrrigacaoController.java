package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.response.PaginaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.RecomendacaoIrrigacaoResponse;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoIrrigacao;
import br.com.agricoladigital.smart_farming_api.service.RecomendacaoIrrigacaoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/** Recomendações de irrigação de uma fazenda: o dono (ou o administrador) consulta e pede novo cálculo. */
@Tag(name = "Recomendações de irrigação", description = "Apoio à decisão de irrigação por talhão: histórico, atuais, detalhe e recálculo.")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/recomendacoes-irrigacao")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class RecomendacaoIrrigacaoController {
    private final RecomendacaoIrrigacaoService service;

    public RecomendacaoIrrigacaoController(RecomendacaoIrrigacaoService service) {
        this.service = service;
    }

    @Operation(summary = "Histórico de recomendações", description = "Paginado, do mais recente para o mais antigo. Filtros: talhaoId, situacao, de, ate, pagina e tamanho (1 a 100).")
    @GetMapping
    public PaginaResponse<RecomendacaoIrrigacaoResponse> listar(
            @PathVariable Long fazendaId,
            @RequestParam(required = false) Long talhaoId,
            @RequestParam(required = false) SituacaoIrrigacao situacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho
    ) {
        return service.listar(fazendaId, talhaoId, situacao, de, ate, pagina, tamanho);
    }

    @Operation(summary = "Recomendações atuais", description = "A mais recente de cada talhão com plantio em cultivo.")
    @GetMapping("/atuais")
    public List<RecomendacaoIrrigacaoResponse> atuais(@PathVariable Long fazendaId) {
        return service.atuais(fazendaId);
    }

    @Operation(summary = "Detalhe da recomendação", description = "Com todos os insumos do cálculo (cultura, umidade, chuva, temperatura e previsão usada).")
    @GetMapping("/{recomendacaoId}")
    public RecomendacaoIrrigacaoResponse buscar(@PathVariable Long fazendaId, @PathVariable Long recomendacaoId) {
        return service.buscar(fazendaId, recomendacaoId);
    }

    @Operation(summary = "Recalcular agora", description = "Recalcula as recomendações da fazenda (cálculo local, sem chamadas externas) e devolve as atuais. Só grava nova recomendação se o resultado mudou ou a anterior venceu.")
    @PostMapping("/calcular")
    public List<RecomendacaoIrrigacaoResponse> calcular(@PathVariable Long fazendaId) {
        return service.calcular(fazendaId);
    }
}
