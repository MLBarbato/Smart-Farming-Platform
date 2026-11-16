package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

@Schema(description = "Talhão de uma fazenda; geometria é GeoJSON Polygon ou nula.", example = "{\"id\": 3, \"identificacao\": \"Talhão Norte\", \"areaHectares\": 48.75, \"geometria\": {\"type\": \"Polygon\", \"coordinates\": [[[-50.93, -17.8], [-50.91, -17.8], [-50.91, -17.78], [-50.93, -17.8]]]}, \"fazendaId\": 1}")
public record TalhaoResponse(
        Long id,
        String identificacao,
        Double areaHectares,
        JsonNode geometria,
        Long fazendaId
) {
}
