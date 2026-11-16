package br.com.agricoladigital.smart_farming_api.client;

import java.time.LocalDateTime;

/**
 * Condições de uma hora cheia (UTC) para um ponto. A precipitação é o acumulado da hora
 * anterior ao instante; a umidade do solo é a umidade volumétrica da camada de 0 a 1 cm
 * (valor de modelo, não medido em campo). Qualquer valor pode ser nulo se o provedor não o
 * trouxer.
 */
public record MedicaoHoraria(
        LocalDateTime horaUtc,
        Double temperaturaCelsius,
        Double umidadeArPercentual,
        Double ventoKmPorHora,
        Double precipitacaoMm,
        Double umidadeSoloPercentual
) {
}
