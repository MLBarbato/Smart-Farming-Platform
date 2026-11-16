package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.response.LeituraAgregadaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.entity.IntervaloAgregacao;
import br.com.agricoladigital.smart_farming_api.dto.response.SensorLeituraAtualResponse;
import br.com.agricoladigital.smart_farming_api.service.LeituraService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@Tag(name = "Leituras", description = "Leituras atuais e históricas dos sensores de um talhão. Instantes em UTC.")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/talhoes/{talhaoId}")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class LeituraController {
    private final LeituraService leituraService;

    public LeituraController(LeituraService leituraService) {
        this.leituraService = leituraService;
    }

    /** Última leitura de cada sensor do talhão. */
    @Operation(summary = "Leituras atuais do talhão", description = "A última leitura de cada sensor do talhão (nula para sensores sem leitura).")
    @GetMapping("/leituras/atuais")
    public List<SensorLeituraAtualResponse> atuais(@PathVariable Long fazendaId, @PathVariable Long talhaoId) {
        return leituraService.atuais(fazendaId, talhaoId);
    }

    /** Histórico do sensor; {@code de} e {@code ate} em ISO-8601 com fuso (padrão: últimas 24 h). */
    @Operation(summary = "Histórico de leituras de um sensor", description = "Filtros: de, ate (ISO-8601 com fuso; padrão: últimas 24 h) e limite (1 a 5000; padrão 500). Em ordem cronológica.")
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

    /** Mínimo, média e máximo por hora ou dia, para gráficos de períodos longos. */
    @Operation(summary = "Leituras agregadas de um sensor", description = "Mínimo, média, máximo e quantidade por HORA (padrão; últimos 7 dias) ou DIA (últimos 30 dias). Filtros: de, ate (ISO-8601 com fuso), intervalo e fuso (IANA, por exemplo America/Sao_Paulo; padrão UTC), que define onde cada intervalo começa. No máximo 1000 intervalos. Intervalos sem leituras não aparecem.")
    @GetMapping("/sensores/{sensorId}/leituras/agregadas")
    public LeituraAgregadaResponse agregadas(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(required = false) IntervaloAgregacao intervalo,
            @RequestParam(required = false) String fuso
    ) {
        return leituraService.agregadas(fazendaId, talhaoId, sensorId, de, ate, intervalo, fuso);
    }

    @Operation(summary = "Última leitura de um sensor")
    @GetMapping("/sensores/{sensorId}/leituras/ultima")
    public LeituraResponse ultima(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long sensorId
    ) {
        return leituraService.ultima(fazendaId, talhaoId, sensorId);
    }
}
