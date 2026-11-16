package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusAlertaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.AlertaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.PaginaResponse;
import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import br.com.agricoladigital.smart_farming_api.security.AcessoService;
import br.com.agricoladigital.smart_farming_api.service.AlertaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/** Alertas de uma fazenda: o dono (ou o administrador) consulta e atende. */
@Tag(name = "Alertas", description = "Alertas gerados automaticamente a cada 15 minutos e o fluxo de atendimento pelo dono da fazenda.")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/alertas")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class AlertaController {
    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @Operation(summary = "Listar alertas da fazenda", description = "Paginada, do mais recente para o mais antigo. Filtros: status (vários, separados por vírgula), evento, criticidade, talhaoId, de e ate (ISO-8601 com fuso), pagina (a partir de 0) e tamanho (1 a 100, padrão 20).")
    @GetMapping
    public PaginaResponse<AlertaResponse> listar(
            @PathVariable Long fazendaId,
            @RequestParam(required = false) List<StatusAlerta> status,
            @RequestParam(required = false) EventoAlerta evento,
            @RequestParam(required = false) CriticidadeAlerta criticidade,
            @RequestParam(required = false) Long talhaoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho
    ) {
        return alertaService.listar(fazendaId, status, evento, criticidade, talhaoId, de, ate, pagina, tamanho);
    }

    @Operation(summary = "Consultar alerta", description = "Inclui a regra do nível atual, a leitura de origem, o atendente e os horários.")
    @GetMapping("/{alertaId}")
    public AlertaResponse buscar(@PathVariable Long fazendaId, @PathVariable Long alertaId) {
        return alertaService.buscar(fazendaId, alertaId);
    }

    @Operation(summary = "Atender alerta", description = "ABERTO para EM_ATENDIMENTO, RESOLVIDO ou DISPENSADO; EM_ATENDIMENTO para RESOLVIDO ou DISPENSADO. Dispensar exige nota. Alerta fechado não reabre.")
    @Erro(codigo = "409", descricao = "Transição não permitida, ou alerta alterado ao mesmo tempo por outra operação")
    @PatchMapping("/{alertaId}/status")
    public AlertaResponse alterarStatus(
            @PathVariable Long fazendaId,
            @PathVariable Long alertaId,
            @Valid @RequestBody AlterarStatusAlertaRequest request,
            Authentication authentication
    ) {
        return alertaService.alterarStatus(fazendaId, alertaId, AcessoService.idDoUsuario(authentication), request);
    }
}
