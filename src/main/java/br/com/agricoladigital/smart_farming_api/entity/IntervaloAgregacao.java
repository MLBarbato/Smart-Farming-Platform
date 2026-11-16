package br.com.agricoladigital.smart_farming_api.entity;

import java.time.Duration;

/** Tamanho do intervalo em que as leituras são agregadas (mínimo, média e máximo). */
public enum IntervaloAgregacao {
    HORA("hour", Duration.ofHours(1), Duration.ofDays(7)),
    DIA("day", Duration.ofDays(1), Duration.ofDays(30));

    private final String unidadeSql;
    private final Duration duracao;
    private final Duration periodoPadrao;

    IntervaloAgregacao(String unidadeSql, Duration duracao, Duration periodoPadrao) {
        this.unidadeSql = unidadeSql;
        this.duracao = duracao;
        this.periodoPadrao = periodoPadrao;
    }

    /** Argumento do {@code date_trunc} do PostgreSQL. */
    public String getUnidadeSql() {
        return unidadeSql;
    }

    public Duration getDuracao() {
        return duracao;
    }

    /** Período consultado quando {@code de} não é informado. */
    public Duration getPeriodoPadrao() {
        return periodoPadrao;
    }
}
