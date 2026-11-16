package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Novo estado da estação (ATIVO, INATIVO ou MANUTENCAO). Estação inativa não é sincronizada.", example = "{\"status\": \"ATIVO\"}")
public record AlterarStatusEstacaoRequest(@NotNull StatusDispositivo status) {
}
