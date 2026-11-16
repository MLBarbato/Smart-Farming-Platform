package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.OrigemLeitura;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;

import java.time.OffsetDateTime;

public record LeituraResponse(
        Long sensorId,
        VariavelMonitorada variavel,
        Double valor,
        String unidade,
        /** Instante da medição, sempre em UTC. */
        OffsetDateTime dataHora,
        OrigemLeitura origem
) {
}
