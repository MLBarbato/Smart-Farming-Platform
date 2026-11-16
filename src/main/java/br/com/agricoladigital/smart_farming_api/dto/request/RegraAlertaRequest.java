package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Ajuste de uma regra de alerta: limiar, janela, descrição e se está ativa.", example = "{\"limiar\": 25.0, \"janelaHoras\": 3, \"ativa\": true, \"descricao\": \"Umidade do solo média abaixo de 25 %\"}")
public record RegraAlertaRequest(
        @NotNull Double limiar,
        @Schema(description = "Obrigatória nos eventos avaliados em janela de tempo; deve ser nula em FALHA_SENSOR.")
        @Min(1) @Max(168) Integer janelaHoras,
        @NotNull Boolean ativa,
        @Size(max = 255) String descricao
) {
}
