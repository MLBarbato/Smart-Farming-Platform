package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cultura do catálogo, com os parâmetros de irrigação quando definidos.", example = "{\"id\": 1, \"nome\": \"Soja\", \"cicloDias\": 120, \"umidadeMinima\": 30.0, \"umidadeIdeal\": 38.0, \"profundidadeRaizCm\": 30, \"parametrosIrrigacaoCompletos\": true}")
public record CulturaResponse(
        Long id,
        String nome,
        Integer cicloDias,
        @Schema(description = "Umidade do solo (%) abaixo da qual se recomenda irrigar; nula se não definida.")
        Double umidadeMinima,
        @Schema(description = "Umidade do solo (%) alvo da irrigação.")
        Double umidadeIdeal,
        Integer profundidadeRaizCm,
        @Schema(description = "Se os parâmetros de irrigação estão completos (sem eles a recomendação é \"dados insuficientes\").")
        boolean parametrosIrrigacaoCompletos
) {
}
