package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;

import java.time.LocalDate;

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
