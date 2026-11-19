package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.config.Erro;
import br.com.agricoladigital.smart_farming_api.entity.TipoRelatorio;
import br.com.agricoladigital.smart_farming_api.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Exportação dos dados de uma fazenda em CSV, para o dono (ou o administrador). */
@Tag(name = "Relatórios", description = "Exportação dos dados de uma fazenda em CSV (separador ponto e vírgula, vírgula decimal, UTF-8 com BOM, horários de Brasília).")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/relatorios")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class RelatorioController {
    static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @Operation(summary = "Exportar relatório em CSV", description = """
            Gera o arquivo do tipo pedido para o período [de, ate] (datas locais de Brasília, as duas incluídas; \
            padrão: os últimos 30 dias; no máximo 366 dias). Tipos: IRRIGACAO (recomendações calculadas no período), \
            CLIMA (leituras horárias da estação virtual), SENSORES (resumo por sensor dos talhões), ALERTAS (alertas \
            abertos no período, com o atendimento) e PRODUTIVIDADE (plantios que ocuparam o período, com colheita e \
            kg/ha). O arquivo vem como anexo (Content-Disposition).""")
    @Erro(codigo = "404", descricao = "Fazenda não encontrada ou, no relatório de clima, fazenda sem estação virtual")
    @GetMapping("/{tipo}")
    public ResponseEntity<byte[]> exportarRelatorio(
            @PathVariable Long fazendaId,
            @Parameter(description = "Tipo de relatório: IRRIGACAO, CLIMA, SENSORES, ALERTAS ou PRODUTIVIDADE")
            @PathVariable TipoRelatorio tipo,
            @Parameter(description = "Primeiro dia do período (AAAA-MM-DD, horário de Brasília); padrão: 29 dias antes do fim")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Último dia do período, incluído (AAAA-MM-DD, horário de Brasília); padrão: hoje")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate
    ) {
        RelatorioService.Arquivo arquivo = service.gerar(fazendaId, tipo, de, ate);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .body(arquivo.conteudo());
    }
}
