package br.com.agricoladigital.smart_farming_api.entity;

import java.util.List;

/**
 * Dados usados no cálculo de uma recomendação de irrigação (todos opcionais, conforme o que havia
 * disponível), gravados junto com ela para que o cálculo possa ser reconstituído depois.
 */
public record InsumosIrrigacao(
        String culturaNome,
        Double umidadeMinima,
        Double umidadeIdeal,
        Integer profundidadeRaizCm,
        Double umidadeAtual,
        OrigemUmidade origemUmidade,
        /** Lâmina necessária para levar o solo à umidade ideal, em mm (antes de chuva e ajuste). */
        Double deficitMm,
        Double chuvaPrevistaMm,
        Double chuvaEfetivaMm,
        Double temperaturaMaximaPrevista,
        boolean ajusteTemperatura,
        List<String> dadosFaltantes
) {
}
