package br.com.agricoladigital.smart_farming_api.client;

import java.time.LocalDate;

/** Previsão de um dia devolvida pelo provedor. Temperaturas em °C e precipitação em mm. */
public record DiaPrevisto(
        LocalDate data,
        Double temperaturaMedia,
        Double temperaturaMinima,
        Double temperaturaMaxima,
        Double precipitacaoMm,
        Integer probabilidadePrecipitacao
) {
}
