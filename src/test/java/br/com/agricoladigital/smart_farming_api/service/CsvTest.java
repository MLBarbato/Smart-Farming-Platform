package br.com.agricoladigital.smart_farming_api.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTest {
    private final Csv csv = new Csv(ZoneId.of("America/Sao_Paulo"));

    private String semBom() {
        return csv.toString().substring(1);
    }

    @Test
    void comecaComBomETerminaCadaLinhaComCrlf() {
        csv.linha("a", "b").linha("c");

        assertEquals('﻿', csv.toString().charAt(0));
        assertEquals("a;b\r\nc\r\n", semBom());
        assertArrayEquals(csv.toString().getBytes(StandardCharsets.UTF_8), csv.bytes());
    }

    @Test
    void formataNumerosComVirgulaDecimalEAteDuasCasas() {
        csv.linha(42.8, 1234.5678, 10, -3.0, null, Double.NaN);

        assertEquals("42,8;1234,57;10;-3;;\r\n", semBom());
    }

    @Test
    void converteInstantesUtcParaOFusoDoRelatorioEFormataDatas() {
        csv.linha(LocalDateTime.of(2026, 11, 19, 2, 5), LocalDate.of(2026, 11, 1));

        assertEquals("18/11/2026 23:05;01/11/2026\r\n", semBom(), "02:05 UTC são 23:05 do dia anterior em Brasília");
    }

    @Test
    void colocaEntreAspasTextosComSeparadorAspasOuQuebraDeLinha() {
        csv.linha("Umidade de 28 %; abaixo do mínimo", "Talhão \"Norte\"", "linha 1\nlinha 2", "simples");

        assertEquals("\"Umidade de 28 %; abaixo do mínimo\";\"Talhão \"\"Norte\"\"\";\"linha 1\nlinha 2\";simples\r\n",
                semBom());
    }

    @Test
    void neutralizaTextosQueSeriamFormulasNaPlanilha() {
        csv.linha("=HYPERLINK(\"x\")", "+1", "-2", "@soma", "normal");

        assertEquals("\"'=HYPERLINK(\"\"x\"\")\";'+1;'-2;'@soma;normal\r\n", semBom());
    }
}
