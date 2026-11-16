package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse;
import br.com.agricoladigital.smart_farming_api.service.MapaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/mapa")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class MapaController {
    private final MapaService mapaService;

    public MapaController(MapaService mapaService) {
        this.mapaService = mapaService;
    }

    /** Fazenda, talhões com polígono, plantio atual, resumo de sensores e últimas leituras. */
    @GetMapping
    public MapaFazendaResponse mapa(@PathVariable Long fazendaId) {
        return mapaService.mapaDaFazenda(fazendaId);
    }
}
