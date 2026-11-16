package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.SensorLeituraAtualResponse;
import br.com.agricoladigital.smart_farming_api.service.LeituraService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/talhoes/{talhaoId}")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class LeituraController {
    private final LeituraService leituraService;

    public LeituraController(LeituraService leituraService) {
        this.leituraService = leituraService;
    }

    /** Última leitura de cada sensor do talhão. */
    @GetMapping("/leituras/atuais")
    public List<SensorLeituraAtualResponse> atuais(@PathVariable Long fazendaId, @PathVariable Long talhaoId) {
        return leituraService.atuais(fazendaId, talhaoId);
    }

    /** Histórico do sensor; {@code de} e {@code ate} em ISO-8601 com fuso (padrão: últimas 24 h). */
    @GetMapping("/sensores/{sensorId}/leituras")
    public List<LeituraResponse> historico(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(required = false) Integer limite
    ) {
        return leituraService.historico(fazendaId, talhaoId, sensorId, de, ate, limite);
    }

    @GetMapping("/sensores/{sensorId}/leituras/ultima")
    public LeituraResponse ultima(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long sensorId
    ) {
        return leituraService.ultima(fazendaId, talhaoId, sensorId);
    }
}
