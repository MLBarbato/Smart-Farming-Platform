package br.com.agricoladigital.smart_farming_api.dto.request;

import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SensorRequest(
        @NotBlank @Size(max = 100) String codigo,
        @NotNull VariavelMonitorada variavel,
        LocalDate dataInstalacao
) {
}
