package br.com.agricoladigital.smart_farming_api.service;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Monta um CSV no formato que o Excel e o LibreOffice em português abrem direto: separador ponto e vírgula,
 * vírgula decimal, UTF-8 com BOM e linhas terminadas em CRLF (RFC 4180).
 *
 * <p>Células de texto são protegidas contra injeção de fórmula: um texto que começa com {@code = + - @}, tab
 * ou CR ganha um apóstrofo na frente. Números não passam por essa proteção, então valores negativos continuam
 * números na planilha.
 */
final class Csv {
    static final char SEPARADOR = ';';
    private static final String FIM_DE_LINHA = "\r\n";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final StringBuilder texto = new StringBuilder("﻿");
    private final DecimalFormat numero = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
    private final ZoneId fuso;

    Csv(ZoneId fuso) {
        this.fuso = fuso;
    }

    /**
     * Acrescenta uma linha. Aceita {@code null} (célula vazia), {@link Number}, {@link LocalDate},
     * {@link LocalDateTime} em UTC (convertido para o fuso do relatório) e texto.
     */
    Csv linha(Object... celulas) {
        for (int i = 0; i < celulas.length; i++) {
            if (i > 0) {
                texto.append(SEPARADOR);
            }
            texto.append(celula(celulas[i]));
        }
        texto.append(FIM_DE_LINHA);
        return this;
    }

    byte[] bytes() {
        return texto.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return texto.toString();
    }

    private String celula(Object valor) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof Double d && (d.isNaN() || d.isInfinite())) {
            return "";
        }
        if (valor instanceof Number n) {
            return numero.format(n);
        }
        if (valor instanceof LocalDateTime utc) {
            return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(fuso).format(DATA_HORA);
        }
        if (valor instanceof LocalDate data) {
            return data.format(DATA);
        }
        return texto(valor.toString());
    }

    private static String texto(String valor) {
        String seguro = !valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
        boolean precisaAspas = seguro.indexOf(SEPARADOR) >= 0 || seguro.indexOf('"') >= 0
                || seguro.indexOf('\n') >= 0 || seguro.indexOf('\r') >= 0;
        return precisaAspas ? '"' + seguro.replace("\"", "\"\"") + '"' : seguro;
    }
}
