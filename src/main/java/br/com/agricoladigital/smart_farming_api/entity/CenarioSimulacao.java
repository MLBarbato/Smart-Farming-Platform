package br.com.agricoladigital.smart_farming_api.entity;

/** Situação que o simulador reproduz para demonstrar alertas. */
public enum CenarioSimulacao {
    /** Valores típicos; nenhum alerta é esperado. */
    NORMAL,
    /** Umidade do solo muito baixa nos sensores de solo dos talhões. */
    SECA,
    /** Temperatura da estação da fazenda próxima de zero. */
    GEADA,
    /** Os sensores dos talhões param de enviar leituras. */
    FALHA_SENSOR
}
