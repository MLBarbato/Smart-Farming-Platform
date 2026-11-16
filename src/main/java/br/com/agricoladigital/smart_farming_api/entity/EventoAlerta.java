package br.com.agricoladigital.smart_farming_api.entity;

public enum EventoAlerta {
    /** Umidade do solo baixa. */
    SECA(true),
    /** Chuva acumulada ou prevista alta. */
    EXCESSO_PRECIPITACAO(true),
    /** Temperatura mínima prevista baixa. */
    RISCO_GEADA(true),
    /** Sensor ativo sem enviar leituras. */
    FALHA_SENSOR(false);

    private final boolean usaJanela;

    EventoAlerta(boolean usaJanela) {
        this.usaJanela = usaJanela;
    }

    /** Se a regra do evento é avaliada sobre uma janela de horas. */
    public boolean usaJanela() {
        return usaJanela;
    }
}
