package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GeradorLeituraSimuladaTest {
    private static final Instant BASE = Instant.parse("2026-06-15T00:00:00Z");

    @Test
    void valoresFicamSempreDentroDaFaixaDeCadaVariavel() {
        GeradorLeituraSimulada gerador = new GeradorLeituraSimulada(new Random(42));
        for (VariavelMonitorada variavel : VariavelMonitorada.values()) {
            for (int minuto = 0; minuto < 24 * 60; minuto += 7) {
                double valor = gerador.gerar(variavel, BASE.plusSeconds(minuto * 60L));
                assertTrue(variavel.aceita(valor), variavel + " fora da faixa: " + valor);
            }
        }
    }

    @Test
    void mesmaSementeProduzMesmaSequencia() {
        GeradorLeituraSimulada a = new GeradorLeituraSimulada(new Random(7));
        GeradorLeituraSimulada b = new GeradorLeituraSimulada(new Random(7));
        for (VariavelMonitorada variavel : VariavelMonitorada.values()) {
            assertEquals(a.gerar(variavel, BASE), b.gerar(variavel, BASE));
        }
    }

    @Test
    void luminosidadeEZeroDeNoiteEPositivaAoMeioDia() {
        GeradorLeituraSimulada gerador = new GeradorLeituraSimulada(new Random(1));
        // 03:00 e 15:00 UTC = 00:00 e 12:00 em Brasília
        assertEquals(0.0, gerador.gerar(VariavelMonitorada.LUMINOSIDADE, Instant.parse("2026-06-15T03:00:00Z")));
        assertTrue(gerador.gerar(VariavelMonitorada.LUMINOSIDADE, Instant.parse("2026-06-15T15:00:00Z")) > 50_000);
    }

    @Test
    void temperaturaEMaisAltaNaTardeDoQueDeMadrugada() {
        GeradorLeituraSimulada gerador = new GeradorLeituraSimulada(new Random(1));
        double madrugada = gerador.gerar(VariavelMonitorada.TEMPERATURA_AR, Instant.parse("2026-06-15T08:00:00Z")); // 05h
        double tarde = gerador.gerar(VariavelMonitorada.TEMPERATURA_AR, Instant.parse("2026-06-15T18:00:00Z"));     // 15h
        assertTrue(tarde > madrugada + 5);
    }

    @Test
    void valoresTemNoMaximoDuasCasasDecimais() {
        GeradorLeituraSimulada gerador = new GeradorLeituraSimulada(new Random(3));
        double valor = gerador.gerar(VariavelMonitorada.PH_SOLO, BASE);
        assertEquals(valor, Math.round(valor * 100) / 100.0);
    }

    @Test
    void historicoCobreAJanelaComUmPontoPorPassoSemIncluirOAgora() {
        GeradorLeituraSimulada gerador = new GeradorLeituraSimulada(new Random(5));
        Instant agora = Instant.parse("2026-06-15T12:00:30Z");

        var pontos = gerador.gerarHistorico(VariavelMonitorada.TEMPERATURA_AR, agora,
                java.time.Duration.ofHours(2), java.time.Duration.ofMinutes(15));

        assertEquals(8, pontos.size());
        assertEquals(Instant.parse("2026-06-15T10:00:30Z").truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                pontos.get(0).instante());
        assertEquals(Instant.parse("2026-06-15T11:45:30Z"), pontos.get(7).instante());
        assertTrue(pontos.stream().allMatch(p -> p.instante().isBefore(agora.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))));
        assertTrue(pontos.stream().allMatch(p -> VariavelMonitorada.TEMPERATURA_AR.aceita(p.valor())));
    }

    @Test
    void historicoRejeitaPassoInvalido() {
        GeradorLeituraSimulada gerador = new GeradorLeituraSimulada(new Random(5));

        assertThrows(IllegalArgumentException.class, () -> gerador.gerarHistorico(
                VariavelMonitorada.PH_SOLO, BASE, java.time.Duration.ofHours(1), java.time.Duration.ZERO));
    }
}
