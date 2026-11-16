package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;

import java.time.LocalDate;

@Schema(description = "Sensor de um talhão.", example = "{\"id\": 12, \"codigo\": \"SOLO-01\", \"variavel\": \"UMIDADE_SOLO\", \"unidade\": \"%\", \"status\": \"ATIVO\", \"dataInstalacao\": \"2026-10-01\", \"talhaoId\": 3}")
public record SensorResponse(
        Long id,
        String codigo,
        VariavelMonitorada variavel,
        String unidade,
        StatusDispositivo status,
        LocalDate dataInstalacao,
        Long talhaoId
) {
}
