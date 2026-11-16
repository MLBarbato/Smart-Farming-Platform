package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Novo estado do sensor (ATIVO, INATIVO ou MANUTENCAO).", example = "{\"status\": \"MANUTENCAO\"}")
public record AlterarStatusSensorRequest(@NotNull StatusDispositivo status) {
}
