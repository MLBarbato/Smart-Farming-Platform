package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.Cultura;
import br.com.agricoladigital.smart_farming_api.entity.OrigemUmidade;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoIrrigacao;
import br.com.agricoladigital.smart_farming_api.service.CalculadoraIrrigacao.Calculo;
import br.com.agricoladigital.smart_farming_api.service.CalculadoraIrrigacao.Entrada;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculadoraIrrigacaoTest {
    private final CalculadoraIrrigacao calculadora = new CalculadoraIrrigacao(0.8, 35, 0.2, 30);
    // mínimo 30 %, ideal 38 %, raízes de 30 cm: cada ponto percentual de umidade vale 3 mm.
    private final Cultura soja = new Cultura("Soja", 120, 30.0, 38.0, 30);

    private Entrada entrada(Cultura cultura, Double umidade, boolean previsaoAtual, Double chuva, Double tmax) {
        return new Entrada(cultura, umidade, umidade == null ? null : OrigemUmidade.SENSOR_TALHAO, previsaoAtual,
                chuva, tmax, 3);
    }

    @Test
    void soloAdequadoNaoPrecisaDeIrrigacaoNemDePrevisao() {
        Calculo c = calculadora.calcular(entrada(soja, 35.0, false, null, null));

        assertEquals(SituacaoIrrigacao.NAO_IRRIGAR, c.situacao());
        assertNull(c.volumeMm());
        assertTrue(c.justificativa().contains("35"), c.justificativa());
        assertTrue(c.justificativa().contains("sensor do talhão"), c.justificativa());
        assertTrue(c.insumos().dadosFaltantes().isEmpty());
    }

    @Test
    void umidadeExatamenteNoMinimoNaoIrriga() {
        assertEquals(SituacaoIrrigacao.NAO_IRRIGAR, calculadora.calcular(entrada(soja, 30.0, false, null, null)).situacao());
    }

    @Test
    void soloSecoIrrigaOQueFaltaDescontandoAChuvaEfetiva() {
        // déficit (38−28) × 3 = 30 mm; chuva 5 mm × 0,8 = 4 mm; volume 26 mm.
        Calculo c = calculadora.calcular(entrada(soja, 28.0, true, 5.0, 28.0));

        assertEquals(SituacaoIrrigacao.IRRIGAR, c.situacao());
        assertEquals(26.0, c.volumeMm());
        assertEquals(30.0, c.insumos().deficitMm(), 0.001);
        assertEquals(4.0, c.insumos().chuvaEfetivaMm(), 0.001);
        assertEquals(5.0, c.insumos().chuvaPrevistaMm());
        assertFalse(c.insumos().ajusteTemperatura());
        assertTrue(c.justificativa().contains("Volume sugerido: 26 mm"), c.justificativa());
        assertTrue(c.justificativa().contains("previsto") || c.justificativa().contains("prevista"), c.justificativa());
    }

    @Test
    void diaQuenteAumentaADemandaEAnotaOAjuste() {
        // 30 × 1,2 = 36 mm − 4 mm = 32 mm, acima do teto de 30 mm.
        Calculo c = calculadora.calcular(entrada(soja, 28.0, true, 5.0, 36.0));

        assertEquals(SituacaoIrrigacao.IRRIGAR, c.situacao());
        assertEquals(30.0, c.volumeMm());
        assertTrue(c.insumos().ajusteTemperatura());
        assertTrue(c.justificativa().contains("+20 %"), c.justificativa());
        assertTrue(c.justificativa().contains("parcele"), c.justificativa());
    }

    @Test
    void volumeNuncaPassaDoTetoPorAplicacao() {
        Calculo c = calculadora.calcular(entrada(soja, 5.0, true, 0.0, 25.0));
        assertEquals(30.0, c.volumeMm());
    }

    @Test
    void chuvaQueCobreADemandaMandaAguardar() {
        // demanda 30 mm; chuva 40 mm × 0,8 = 32 mm.
        Calculo c = calculadora.calcular(entrada(soja, 28.0, true, 40.0, 28.0));

        assertEquals(SituacaoIrrigacao.AGUARDAR_CHUVA, c.situacao());
        assertNull(c.volumeMm());
        assertTrue(c.justificativa().contains("aguarde"), c.justificativa());
    }

    @Test
    void semTemperaturaPrevistaCalculaSemAjuste() {
        Calculo c = calculadora.calcular(entrada(soja, 28.0, true, 5.0, null));
        assertEquals(SituacaoIrrigacao.IRRIGAR, c.situacao());
        assertEquals(26.0, c.volumeMm());
    }

    @Test
    void culturaSemParametrosNaoRecomenda() {
        Calculo c = calculadora.calcular(entrada(new Cultura("Milho", 130), 28.0, true, 0.0, 28.0));

        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, c.situacao());
        assertNull(c.volumeMm());
        assertEquals(1, c.insumos().dadosFaltantes().size());
        assertTrue(c.insumos().dadosFaltantes().get(0).contains("Milho"));
        assertTrue(c.justificativa().startsWith("Dados insuficientes"), c.justificativa());
    }

    @Test
    void semLeituraDeUmidadeNaoRecomenda() {
        Calculo c = calculadora.calcular(entrada(soja, null, true, 0.0, 28.0));

        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, c.situacao());
        assertTrue(c.insumos().dadosFaltantes().get(0).contains("umidade do solo"));
        assertNull(c.insumos().umidadeAtual());
    }

    @Test
    void soloSecoSemPrevisaoAtualNaoRecomendaComFalsaCerteza() {
        Calculo c = calculadora.calcular(entrada(soja, 20.0, false, null, null));

        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, c.situacao());
        assertNull(c.volumeMm());
        assertTrue(c.insumos().dadosFaltantes().stream().anyMatch(f -> f.contains("previsão do tempo")));
    }

    @Test
    void previsaoAtualSemChuvaInformadaTambemNaoRecomenda() {
        Calculo c = calculadora.calcular(entrada(soja, 20.0, true, null, 30.0));
        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, c.situacao());
        assertTrue(c.insumos().dadosFaltantes().stream().anyMatch(f -> f.contains("chuva prevista")));
    }

    @Test
    void listaTodosOsDadosQueFaltam() {
        Calculo c = calculadora.calcular(entrada(new Cultura("Milho", 130), null, false, null, null));
        assertEquals(2, c.insumos().dadosFaltantes().size());
    }

    @Test
    void origemEstacaoEDeclaradaComoValorDeModelo() {
        Calculo c = calculadora.calcular(new Entrada(soja, 35.0, OrigemUmidade.ESTACAO, false, null, null, 3));
        assertTrue(c.justificativa().contains("estação da fazenda"), c.justificativa());
        assertTrue(c.justificativa().contains("modelo"), c.justificativa());
        assertEquals(OrigemUmidade.ESTACAO, c.insumos().origemUmidade());
    }

    @Test
    void justificativaCabeNaColuna() {
        Calculo c = calculadora.calcular(entrada(new Cultura("X".repeat(100), 100, 30.0, 38.0, 30), 28.0, true, 5.0, 36.0));
        assertTrue(c.justificativa().length() <= 500);
    }

    @Test
    void configuracaoInvalidaEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> new CalculadoraIrrigacao(1.5, 35, 0.2, 30));
        assertThrows(IllegalArgumentException.class, () -> new CalculadoraIrrigacao(0.8, 35, -1, 30));
        assertThrows(IllegalArgumentException.class, () -> new CalculadoraIrrigacao(0.8, 35, 0.2, 0));
    }
}
