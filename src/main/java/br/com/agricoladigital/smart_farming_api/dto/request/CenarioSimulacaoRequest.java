package br.com.agricoladigital.smart_farming_api.dto.request;

import br.com.agricoladigital.smart_farming_api.entity.CenarioSimulacao;
import jakarta.validation.constraints.NotNull;

/** {@code fazendaId} é opcional: sem ele, o cenário vale para todas as fazendas. */
public record CenarioSimulacaoRequest(@NotNull CenarioSimulacao cenario, Long fazendaId) {
}
