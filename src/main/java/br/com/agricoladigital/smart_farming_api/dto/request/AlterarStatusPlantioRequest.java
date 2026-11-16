package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

@Schema(description = "Mudança de estado do plantio. COLHIDO exige dataColheitaReal; quantidadeColhidaKg é opcional.", example = "{\"status\": \"COLHIDO\", \"dataColheitaReal\": \"2027-02-10\", \"quantidadeColhidaKg\": 54000}")
public record AlterarStatusPlantioRequest(
        @NotNull StatusPlantio status,
        LocalDate dataColheitaReal,
        @Positive Double quantidadeColhidaKg
) {
}
