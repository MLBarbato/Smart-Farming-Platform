package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Plantio de uma cultura em um talhão. Nasce PLANEJADO; a colheita prevista é calculada pelo ciclo da cultura quando não informada.", example = "{\"culturaId\": 1, \"tipoPlantio\": \"Plantio direto\", \"safra\": \"2026/2027\", \"dataPlantio\": \"2026-10-01\"}")
public record PlantioRequest(
        @NotNull Long culturaId,
        @Size(max = 100) String tipoPlantio,
        @Pattern(regexp = "\\d{4}/\\d{4}", message = "deve seguir o formato AAAA/AAAA, por exemplo 2026/2027")
        String safra,
        @NotNull LocalDate dataPlantio,
        LocalDate dataColheitaPrevista
) {
}
