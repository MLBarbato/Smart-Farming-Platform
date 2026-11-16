package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse;
import br.com.agricoladigital.smart_farming_api.service.MapaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mapa", description = "Dados para desenhar o mapa de uma fazenda (polígonos, sensores, alertas e recomendações).")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/mapa")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class MapaController {
    private final MapaService mapaService;

    public MapaController(MapaService mapaService) {
        this.mapaService = mapaService;
    }

    /** Fazenda, talhões com polígono, plantio atual, resumo de sensores e últimas leituras. */
    @Operation(summary = "Dados do mapa da fazenda", description = "Ponto da fazenda e, por talhão: polígono, plantio em cultivo, sensores, leituras atuais, alertas abertos (maiorCriticidade colore o polígono) e recomendação de irrigação.")
    @GetMapping
    public MapaFazendaResponse mapa(@PathVariable Long fazendaId) {
        return mapaService.mapaDaFazenda(fazendaId);
    }
}
