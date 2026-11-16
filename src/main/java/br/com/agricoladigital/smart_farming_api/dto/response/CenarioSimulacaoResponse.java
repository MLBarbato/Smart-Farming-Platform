package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.CenarioSimulacao;

import java.util.List;
import java.util.Map;

@Schema(description = "Resultado da troca de cenário do simulador, incluindo a avaliação de alertas feita na hora.", example = "{\"cenario\": \"SECA\", \"fazendas\": [1], \"leiturasAjustadas\": 48, \"alertas\": {\"criados\": 2, \"escalados\": 0, \"resolvidosAutomaticamente\": 0}, \"avisos\": []}")
public record CenarioSimulacaoResponse(
        CenarioSimulacao cenario,
        List<Long> fazendas,
        int leiturasAjustadas,
        @Schema(description = "Resultado da avaliação de alertas feita logo após a troca.")
        Alertas alertas,
        List<String> avisos
) {
    public record Alertas(int criados, int escalados, int resolvidosAutomaticamente) {
    }

    @Schema(description = "Estado atual: cenário padrão e exceções por fazenda.")
    public record Estado(CenarioSimulacao padrao, Map<Long, CenarioSimulacao> porFazenda) {
    }
}
