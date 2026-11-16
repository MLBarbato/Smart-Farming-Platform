package br.com.agricoladigital.smart_farming_api.entity;

public enum OrigemLeitura {
    /** Enviada por um sensor físico. */
    REAL,
    /** Gerada pelo simulador de sensores. */
    SIMULADA,
    /** Obtida de uma API externa, como a de previsão do tempo. */
    EXTERNA
}
