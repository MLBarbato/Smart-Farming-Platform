package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.response.PrevisaoFazendaResponse;
import br.com.agricoladigital.smart_farming_api.service.PrevisaoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/previsao")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class PrevisaoController {
    private final PrevisaoService previsaoService;

    public PrevisaoController(PrevisaoService previsaoService) {
        this.previsaoService = previsaoService;
    }

    @GetMapping
    public PrevisaoFazendaResponse consultar(@PathVariable Long fazendaId) {
        return previsaoService.consultar(fazendaId);
    }

    /** Força a atualização junto ao provedor (limitada por um intervalo mínimo). */
    @PostMapping("/atualizar")
    public PrevisaoFazendaResponse atualizar(@PathVariable Long fazendaId) {
        return previsaoService.atualizarManualmente(fazendaId);
    }
}
