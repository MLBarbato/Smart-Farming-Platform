package br.com.agricoladigital.smart_farming_api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record RegistrarColheitaRequest(
        @NotNull LocalDate dataColheitaReal,
        @Positive Double quantidadeColhidaKg
) {
}
