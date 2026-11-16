package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fazenda do usuário.", example = "{\"id\": 1, \"nome\": \"Fazenda Horizonte\", \"localizacao\": \"Zona rural\", \"areaTotal\": 1200.5, \"usuarioId\": 1, \"ativa\": true, \"latitude\": -17.7923, \"longitude\": -50.9192, \"cidade\": \"Rio Verde\", \"estado\": \"GO\"}")
public record FazendaResponse(
        Long id,
        String nome,
        String localizacao,
        Double areaTotal,
        Long usuarioId,
        boolean ativa,
        Double latitude,
        Double longitude,
        String cidade,
        String estado
) {
}
