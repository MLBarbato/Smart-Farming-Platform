package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.config.Erro;
import br.com.agricoladigital.smart_farming_api.dto.response.AlertaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.PaginaResponse;
import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import br.com.agricoladigital.smart_farming_api.security.AcessoService;
import br.com.agricoladigital.smart_farming_api.service.AlertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/** Alertas de todas as fazendas do usuário (de todas, para o administrador), para a visão geral e o sino. */
@Tag(name = "Alertas", description = "Alertas gerados automaticamente a cada 15 minutos e o fluxo de atendimento pelo dono da fazenda.")
@RestController
@RequestMapping("/api/v1/alertas")
public class AlertaGlobalController {
    private final AlertaService alertaService;

    public AlertaGlobalController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @Operation(summary = "Listar alertas de todas as minhas fazendas",
            description = "Paginada, do mais recente para o mais antigo. Agricultor: alertas das próprias fazendas; "
                    + "administrador: de todas. Filtros: fazendaId, status (vários, separados por vírgula), evento, "
                    + "criticidade, talhaoId, de e ate (ISO-8601 com fuso), pagina (a partir de 0) e tamanho (1 a 100, padrão 20). "
                    + "Para consultar e atender um alerta use as rotas da fazenda (/fazendas/{fazendaId}/alertas/{alertaId}).")
    @Erro(codigo = "404", descricao = "fazendaId inexistente ou de outro usuário")
    @GetMapping
    public PaginaResponse<AlertaResponse> listar(
            Authentication authentication,
            @RequestParam(required = false) Long fazendaId,
            @RequestParam(required = false) List<StatusAlerta> status,
            @RequestParam(required = false) EventoAlerta evento,
            @RequestParam(required = false) CriticidadeAlerta criticidade,
            @RequestParam(required = false) Long talhaoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho
    ) {
        return alertaService.listarDoUsuario(
                AcessoService.idDoUsuario(authentication), AcessoService.ehAdministrador(authentication),
                fazendaId, status, evento, criticidade, talhaoId, de, ate, pagina, tamanho);
    }
}
