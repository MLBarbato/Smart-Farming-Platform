package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoGeralResponse;
import br.com.agricoladigital.smart_farming_api.security.AcessoService;
import br.com.agricoladigital.smart_farming_api.service.ResumoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Resumos", description = "Indicadores agregados para o dashboard: geral e por fazenda.")
@RestController
@RequestMapping("/api/v1")
public class ResumoController {
    private final ResumoService resumoService;

    public ResumoController(ResumoService resumoService) {
        this.resumoService = resumoService;
    }

    /** Visão geral das fazendas do usuário (administrador: de todas). */
    @Operation(summary = "Resumo geral", description = "Fazendas do usuário (todas, para o administrador): totais e um cartão por fazenda, incluindo alertas e irrigação.")
    @GetMapping("/resumo")
    public ResumoGeralResponse resumoGeral(Authentication authentication) {
        return resumoService.resumoGeral(
                AcessoService.ehAdministrador(authentication) ? null : AcessoService.idDoUsuario(authentication)
        );
    }

    /** Indicadores de uma fazenda para o dashboard. */
    @PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
    @Operation(summary = "Resumo da fazenda", description = "Talhões, sensores, plantios, culturas, condições atuais, produtividade por safra, estação, alertas abertos e irrigação.")
    @GetMapping("/fazendas/{fazendaId}/resumo")
    public ResumoFazendaResponse resumoDaFazenda(@PathVariable Long fazendaId) {
        return resumoService.resumoDaFazenda(fazendaId);
    }
}
