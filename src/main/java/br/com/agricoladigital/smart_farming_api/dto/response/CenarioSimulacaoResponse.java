package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.CenarioSimulacao;

import java.util.List;
import java.util.Map;

public record CenarioSimulacaoResponse(
        CenarioSimulacao cenario,
        List<Long> fazendas,
        int leiturasAjustadas,
        /** Resultado da avaliação de alertas feita logo após a troca. */
        Alertas alertas,
        List<String> avisos
) {
    public record Alertas(int criados, int escalados, int resolvidosAutomaticamente) {
    }

    /** Estado atual: cenário padrão e exceções por fazenda. */
    public record Estado(CenarioSimulacao padrao, Map<Long, CenarioSimulacao> porFazenda) {
    }
}
