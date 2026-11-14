package br.com.agricoladigital.smart_farming_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CulturaRequest(
        @NotBlank @Size(max = 100) String nome,
        @Positive Integer cicloDias
) {
}
