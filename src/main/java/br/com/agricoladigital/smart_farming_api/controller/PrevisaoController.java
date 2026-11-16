package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.response.PrevisaoFazendaResponse;
import br.com.agricoladigital.smart_farming_api.service.PrevisaoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Previsão do tempo", description = "Previsão diária por fazenda (Open-Meteo). Exiba sempre a atribuição devolvida.")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/previsao")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class PrevisaoController {
    private final PrevisaoService previsaoService;

    public PrevisaoController(PrevisaoService previsaoService) {
        this.previsaoService = previsaoService;
    }

    @Operation(summary = "Consultar previsão", description = "Devolve a previsão gravada e sua situação (ATUAL, DESATUALIZADA, SEM_DADOS ou SEM_COORDENADAS). Exiba a atribuição.")
    @GetMapping
    public PrevisaoFazendaResponse consultar(@PathVariable Long fazendaId) {
        return previsaoService.consultar(fazendaId);
    }

    /** Força a atualização junto ao provedor (limitada por um intervalo mínimo). */
    @Operation(summary = "Atualizar previsão agora", description = "Consulta o provedor (com intervalo mínimo entre chamadas). A previsão anterior é mantida se o provedor falhar.")
    @Erro(codigo = "400", descricao = "Fazenda sem latitude e longitude")
    @Erro(codigo = "503", descricao = "Provedor de previsão indisponível")
    @PostMapping("/atualizar")
    public PrevisaoFazendaResponse atualizar(@PathVariable Long fazendaId) {
        return previsaoService.atualizarManualmente(fazendaId);
    }
}
