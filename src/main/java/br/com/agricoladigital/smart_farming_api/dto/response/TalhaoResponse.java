package br.com.agricoladigital.smart_farming_api.dto.response;

import tools.jackson.databind.JsonNode;

public record TalhaoResponse(
        Long id,
        String identificacao,
        Double areaHectares,
        JsonNode geometria,
        Long fazendaId
) {
}
