package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.OperadorRegra;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;

public record RegraAlertaResponse(
        Long id,
        EventoAlerta evento,
        CriticidadeAlerta criticidade,
        OperadorRegra operador,
        VariavelMonitorada variavel,
        Double limiar,
        String unidade,
        Integer janelaHoras,
        Boolean ativa,
        String descricao
) {
}
