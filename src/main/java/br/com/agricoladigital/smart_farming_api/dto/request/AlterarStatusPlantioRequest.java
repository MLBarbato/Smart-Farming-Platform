package br.com.agricoladigital.smart_farming_api.dto.request;

import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Os campos de colheita só valem quando o novo status é COLHIDO. A data real assume o dia atual
 * se não for informada.
 */
public record AlterarStatusPlantioRequest(
        @NotNull StatusPlantio status,
        LocalDate dataColheitaReal,
        @Positive Double quantidadeColhidaKg
) {
}
