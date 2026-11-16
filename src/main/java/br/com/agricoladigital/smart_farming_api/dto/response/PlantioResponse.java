package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;

import java.time.LocalDate;

@Schema(description = "Plantio de um talhão; produtividade só existe depois da colheita.", example = "{\"id\": 7, \"talhaoId\": 3, \"culturaId\": 1, \"culturaNome\": \"Soja\", \"tipoPlantio\": \"Plantio direto\", \"safra\": \"2026/2027\", \"dataPlantio\": \"2026-10-01\", \"dataColheitaPrevista\": \"2027-01-29\", \"dataColheitaReal\": null, \"quantidadeColhidaKg\": null, \"produtividadeKgPorHectare\": null, \"status\": \"EM_CULTIVO\"}")
public record PlantioResponse(
        Long id,
        Long talhaoId,
        Long culturaId,
        String culturaNome,
        String tipoPlantio,
        String safra,
        LocalDate dataPlantio,
        LocalDate dataColheitaPrevista,
        LocalDate dataColheitaReal,
        Double quantidadeColhidaKg,
        Double produtividadeKgPorHectare,
        StatusPlantio status
) {
}
