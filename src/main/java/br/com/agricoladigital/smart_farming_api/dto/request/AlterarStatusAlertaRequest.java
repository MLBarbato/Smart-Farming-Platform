package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Mudança de estado de um alerta: EM_ATENDIMENTO, RESOLVIDO ou DISPENSADO. Dispensar exige nota.", example = "{\"status\": \"EM_ATENDIMENTO\", \"nota\": \"Irrigação iniciada\"}")
public record AlterarStatusAlertaRequest(
        @NotNull StatusAlerta status,
        @Size(max = 500) String nota
) {
}
