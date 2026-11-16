package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.OperadorRegra;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;

@Schema(description = "Regra de alerta: para um evento, cada criticidade tem operador, limiar e janela próprios.", example = "{\"id\": 3, \"evento\": \"SECA\", \"criticidade\": \"ALTA\", \"operador\": \"MENOR_QUE\", \"variavel\": \"UMIDADE_SOLO\", \"limiar\": 20.0, \"unidade\": \"%\", \"janelaHoras\": 3, \"ativa\": true, \"descricao\": \"Umidade do solo média abaixo de 20 %\"}")
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
