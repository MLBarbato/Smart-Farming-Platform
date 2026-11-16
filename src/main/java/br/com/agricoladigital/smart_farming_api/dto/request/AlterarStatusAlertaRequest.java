package br.com.agricoladigital.smart_farming_api.dto.request;

import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code nota} é obrigatória para dispensar um alerta e opcional nas demais mudanças. */
public record AlterarStatusAlertaRequest(
        @NotNull StatusAlerta status,
        @Size(max = 500) String nota
) {
}
