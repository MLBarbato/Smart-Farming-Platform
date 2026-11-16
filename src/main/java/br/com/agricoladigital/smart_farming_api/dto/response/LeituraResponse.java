package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.OrigemLeitura;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;

import java.time.OffsetDateTime;

@Schema(description = "Leitura de um sensor; dataHora em UTC.", example = "{\"sensorId\": 12, \"variavel\": \"UMIDADE_SOLO\", \"valor\": 21.4, \"unidade\": \"%\", \"dataHora\": \"2026-11-16T15:30:00Z\", \"origem\": \"SIMULADA\"}")
public record LeituraResponse(
        Long sensorId,
        VariavelMonitorada variavel,
        Double valor,
        String unidade,
        @Schema(description = "Instante da medição, sempre em UTC.")
        OffsetDateTime dataHora,
        OrigemLeitura origem
) {
}
