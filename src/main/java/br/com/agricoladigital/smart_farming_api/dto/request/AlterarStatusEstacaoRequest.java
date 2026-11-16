package br.com.agricoladigital.smart_farming_api.dto.request;

import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import jakarta.validation.constraints.NotNull;

public record AlterarStatusEstacaoRequest(@NotNull StatusDispositivo status) {
}
