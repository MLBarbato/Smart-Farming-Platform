package br.com.agricoladigital.smart_farming_api.dto.request;

import br.com.agricoladigital.smart_farming_api.dto.validation.PoligonoGeoJson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record TalhaoRequest(
        @NotBlank @Size(max = 100) String identificacao,
        @NotNull @Positive Double areaHectares,
        @PoligonoGeoJson JsonNode geometria
) {
}
