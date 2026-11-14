package br.com.agricoladigital.smart_farming_api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PlantioRequest(
        @NotNull Long culturaId,
        @Size(max = 100) String tipoPlantio,
        @Pattern(regexp = "\\d{4}/\\d{4}", message = "deve seguir o formato AAAA/AAAA, por exemplo 2026/2027")
        String safra,
        @NotNull LocalDate dataPlantio,
        LocalDate dataColheitaPrevista
) {
}
