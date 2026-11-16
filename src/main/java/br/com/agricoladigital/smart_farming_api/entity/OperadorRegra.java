package br.com.agricoladigital.smart_farming_api.entity;

public enum OperadorRegra {
    MENOR_QUE,
    MENOR_OU_IGUAL,
    MAIOR_QUE,
    MAIOR_OU_IGUAL;

    /** Verdadeiro se {@code valor} satisfaz a condição em relação ao limiar. */
    public boolean satisfeito(double valor, double limiar) {
        return switch (this) {
            case MENOR_QUE -> valor < limiar;
            case MENOR_OU_IGUAL -> valor <= limiar;
            case MAIOR_QUE -> valor > limiar;
            case MAIOR_OU_IGUAL -> valor >= limiar;
        };
    }

    /** Se um valor menor é a direção "pior" (limiares mais severos são mais baixos). */
    public boolean piorQuandoMenor() {
        return this == MENOR_QUE || this == MENOR_OU_IGUAL;
    }

    public String simbolo() {
        return switch (this) {
            case MENOR_QUE -> "<";
            case MENOR_OU_IGUAL -> "≤";
            case MAIOR_QUE -> ">";
            case MAIOR_OU_IGUAL -> "≥";
        };
    }
}
