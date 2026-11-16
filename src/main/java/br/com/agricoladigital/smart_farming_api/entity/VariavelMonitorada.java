package br.com.agricoladigital.smart_farming_api.entity;

/**
 * Variáveis ambientais monitoradas, com a unidade fixa e a faixa plausível de cada uma.
 * Leituras fora da faixa são rejeitadas na ingestão.
 */
public enum VariavelMonitorada {
    TEMPERATURA_AR("°C", -10, 50),
    UMIDADE_AR("%", 0, 100),
    UMIDADE_SOLO("%", 0, 100),
    LUMINOSIDADE("lux", 0, 150_000),
    PH_SOLO("pH", 3, 9),
    VELOCIDADE_VENTO("km/h", 0, 150),
    PRECIPITACAO("mm", 0, 200),
    TEMPERATURA_SOLO("°C", -10, 60),
    CONDUTIVIDADE_ELETRICA("dS/m", 0, 20);

    private final String unidade;
    private final double minimo;
    private final double maximo;

    VariavelMonitorada(String unidade, double minimo, double maximo) {
        this.unidade = unidade;
        this.minimo = minimo;
        this.maximo = maximo;
    }

    public String getUnidade() {
        return unidade;
    }

    public double getMinimo() {
        return minimo;
    }

    public double getMaximo() {
        return maximo;
    }

    public boolean aceita(double valor) {
        return Double.isFinite(valor) && valor >= minimo && valor <= maximo;
    }
}
