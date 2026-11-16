package br.com.agricoladigital.smart_farming_api.entity;

public enum SituacaoPrevisao {
    /** Consultada há pouco, dentro da validade. */
    ATUAL,
    /** Existe, mas a última consulta bem-sucedida passou da validade; use com cautela. */
    DESATUALIZADA,
    /** Nenhuma previsão ainda foi obtida para a fazenda. */
    SEM_DADOS,
    /** A fazenda não tem latitude e longitude cadastradas. */
    SEM_COORDENADAS
}
