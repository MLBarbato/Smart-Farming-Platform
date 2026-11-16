package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Cultura do catálogo. Os três parâmetros de irrigação são opcionais, mas devem vir juntos (umidadeMinima < umidadeIdeal).", example = "{\"nome\": \"Soja\", \"cicloDias\": 120, \"umidadeMinima\": 30.0, \"umidadeIdeal\": 38.0, \"profundidadeRaizCm\": 30}")
public record CulturaRequest(
        @NotBlank @Size(max = 100) String nome,
        @Positive Integer cicloDias,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("100.0") Double umidadeMinima,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("100.0") Double umidadeIdeal,
        @Positive @Max(300) Integer profundidadeRaizCm
) {
    public CulturaRequest(String nome, Integer cicloDias) {
        this(nome, cicloDias, null, null, null);
    }
}
