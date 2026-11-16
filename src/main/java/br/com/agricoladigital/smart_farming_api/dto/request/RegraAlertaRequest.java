package br.com.agricoladigital.smart_farming_api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Campos que o administrador pode ajustar; evento, criticidade, operador e variável são fixos. */
public record RegraAlertaRequest(
        @NotNull Double limiar,
        /** Obrigatória nos eventos avaliados em janela de tempo; deve ser nula em FALHA_SENSOR. */
        @Min(1) @Max(168) Integer janelaHoras,
        @NotNull Boolean ativa,
        @Size(max = 255) String descricao
) {
}
