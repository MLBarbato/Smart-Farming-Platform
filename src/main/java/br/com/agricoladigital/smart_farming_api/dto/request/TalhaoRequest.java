package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.dto.validation.PoligonoGeoJson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

@Schema(description = "Talhão. geometria é um GeoJSON Polygon ([longitude, latitude], anel fechado) e é opcional.", example = "{\"identificacao\": \"Talhão Norte\", \"areaHectares\": 48.75, \"geometria\": {\"type\": \"Polygon\", \"coordinates\": [[[-50.93, -17.8], [-50.91, -17.8], [-50.91, -17.78], [-50.93, -17.78], [-50.93, -17.8]]]}}")
public record TalhaoRequest(
        @NotBlank @Size(max = 100) String identificacao,
        @NotNull @Positive Double areaHectares,
        @PoligonoGeoJson JsonNode geometria
) {
}
