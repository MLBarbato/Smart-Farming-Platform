package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;

import java.time.LocalDate;

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
