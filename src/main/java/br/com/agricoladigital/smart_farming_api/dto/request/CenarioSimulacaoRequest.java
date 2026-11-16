package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.CenarioSimulacao;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Cenário do simulador de demonstração; sem fazendaId vale para todas as fazendas.", example = "{\"cenario\": \"SECA\", \"fazendaId\": 1}")
public record CenarioSimulacaoRequest(@NotNull CenarioSimulacao cenario, Long fazendaId) {
}
