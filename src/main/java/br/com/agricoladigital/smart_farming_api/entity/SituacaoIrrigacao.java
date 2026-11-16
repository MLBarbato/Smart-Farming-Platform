package br.com.agricoladigital.smart_farming_api.entity;

public enum SituacaoIrrigacao {
    /** O solo está abaixo do mínimo da cultura e a chuva prevista não cobre a demanda. */
    IRRIGAR,
    /** A umidade do solo está adequada. */
    NAO_IRRIGAR,
    /** O solo precisa de água, mas a chuva prevista cobre a demanda. */
    AGUARDAR_CHUVA,
    /** Faltam dados confiáveis; nenhuma sugestão é feita (ver {@code dadosFaltantes}). */
    SEM_DADOS_SUFICIENTES
}
