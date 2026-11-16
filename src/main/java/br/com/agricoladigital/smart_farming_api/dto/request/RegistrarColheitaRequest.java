package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

@Schema(description = "Correção da data e da quantidade de um plantio já COLHIDO.", example = "{\"dataColheitaReal\": \"2027-02-10\", \"quantidadeColhidaKg\": 54000}")
public record RegistrarColheitaRequest(
        @NotNull LocalDate dataColheitaReal,
        @Positive Double quantidadeColhidaKg
) {
}
