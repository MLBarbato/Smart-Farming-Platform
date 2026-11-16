package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Sensor de um talhão. O código é único; a unidade vem fixa da variável.", example = "{\"codigo\": \"SOLO-01\", \"variavel\": \"UMIDADE_SOLO\", \"dataInstalacao\": \"2026-10-01\"}")
public record SensorRequest(
        @NotBlank @Size(max = 100) String codigo,
        @NotNull VariavelMonitorada variavel,
        LocalDate dataInstalacao
) {
}
