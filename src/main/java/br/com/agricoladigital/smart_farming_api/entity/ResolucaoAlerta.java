package br.com.agricoladigital.smart_farming_api.entity;

public enum ResolucaoAlerta {
    /** O responsável marcou o alerta como resolvido. */
    MANUAL,
    /** A condição normalizou enquanto ninguém havia assumido o alerta. */
    AUTOMATICA
}
