package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Gera valores plausíveis para cada variável: ciclo diário (pela hora de Brasília) mais ruído,
 * sempre dentro da faixa válida da variável. Não guarda estado entre chamadas.
 */
public class GeradorLeituraSimulada {
    private static final ZoneId FUSO_LOCAL = ZoneId.of("America/Sao_Paulo");
    private static final double CHANCE_DE_CHUVA = 0.05;

    /** Valores fixos dos cenários de demonstração (ver {@code CenarioSimulacao}). */
    public static final double UMIDADE_SOLO_EM_SECA = 15.0;
    public static final double UMIDADE_SOLO_NORMAL = 45.0;
    public static final double TEMPERATURA_EM_GEADA = 0.5;
    public static final double TEMPERATURA_NORMAL = 22.0;

    private final RandomGenerator aleatorio;

    public GeradorLeituraSimulada(RandomGenerator aleatorio) {
        this.aleatorio = aleatorio;
    }

    /** Valor simulado de uma variável em um instante. */
    public record Ponto(Instant instante, double valor) {
    }

    /**
     * Série retroativa: um ponto a cada {@code passo}, do mais antigo até {@code passo} antes de
     * {@code agora} (o próprio "agora" fica para a leitura atual do simulador). Os instantes são
     * truncados ao segundo.
     */
    public List<Ponto> gerarHistorico(VariavelMonitorada variavel, Instant agora, Duration janela, Duration passo) {
        if (passo.isZero() || passo.isNegative() || janela.isNegative()) {
            throw new IllegalArgumentException("janela e passo devem ser positivos.");
        }
        Instant referencia = agora.truncatedTo(ChronoUnit.SECONDS);
        List<Ponto> pontos = new ArrayList<>();
        for (Instant instante = referencia.minus(janela); instante.isBefore(referencia.minus(passo).plusSeconds(1));
             instante = instante.plus(passo)) {
            pontos.add(new Ponto(instante, gerar(variavel, instante)));
        }
        return pontos;
    }

    public double gerar(VariavelMonitorada variavel, Instant instante) {
        double hora = instante.atZone(FUSO_LOCAL).getHour()
                + instante.atZone(FUSO_LOCAL).getMinute() / 60.0;
        double valor = switch (variavel) {
            case TEMPERATURA_AR -> ciclo(24, 6, hora, 15) + ruido(0.5);
            case UMIDADE_AR -> ciclo(65, 20, hora, 5) + ruido(2);
            case UMIDADE_SOLO -> ciclo(45, 3, hora, 17) + ruido(1);
            case LUMINOSIDADE -> hora > 6 && hora < 18
                    ? 90_000 * Math.sin(Math.PI * (hora - 6) / 12) + ruido(3_000)
                    : 0;
            case PH_SOLO -> 6.3 + ruido(0.05);
            case VELOCIDADE_VENTO -> ciclo(8, 4, hora, 14) + ruido(2);
            case PRECIPITACAO -> aleatorio.nextDouble() < CHANCE_DE_CHUVA ? 0.2 + aleatorio.nextDouble() * 4.8 : 0;
        };
        double limitado = Math.max(variavel.getMinimo(), Math.min(variavel.getMaximo(), valor));
        return Math.round(limitado * 100) / 100.0;
    }

    /** Onda de 24 h com pico em {@code horaDoPico}. */
    private static double ciclo(double media, double amplitude, double hora, double horaDoPico) {
        return media + amplitude * Math.cos(2 * Math.PI * (hora - horaDoPico) / 24);
    }

    private double ruido(double desvio) {
        return (aleatorio.nextDouble() * 2 - 1) * desvio;
    }
}
