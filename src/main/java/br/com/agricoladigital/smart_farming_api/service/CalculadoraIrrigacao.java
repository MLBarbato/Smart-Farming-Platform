package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.Cultura;
import br.com.agricoladigital.smart_farming_api.entity.InsumosIrrigacao;
import br.com.agricoladigital.smart_farming_api.entity.OrigemUmidade;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoIrrigacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lógica da recomendação de irrigação (balanço pela umidade do solo), sem acesso a banco: recebe os
 * insumos e devolve o resultado com a justificativa.
 *
 * <ol>
 *   <li>Sem parâmetros da cultura, sem umidade do solo ou sem previsão atual: SEM_DADOS_SUFICIENTES.</li>
 *   <li>Umidade igual ou acima do mínimo da cultura: NAO_IRRIGAR.</li>
 *   <li>Abaixo do mínimo: déficit = (ideal − atual)/100 × profundidade da raiz (mm); +20 % se a
 *       máxima prevista chega a 35 °C; menos a chuva efetiva (80 % da prevista). Se a chuva cobre a
 *       demanda: AGUARDAR_CHUVA; senão IRRIGAR, com teto por aplicação.</li>
 * </ol>
 *
 * <p>Parâmetros de demonstração, não validados por agrônomo: o resultado apoia, não substitui, a decisão.
 */
@Component
public class CalculadoraIrrigacao {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final int MAX_JUSTIFICATIVA = 500;

    private final double eficienciaChuva;
    private final double temperaturaAltaC;
    private final double acrescimoTemperatura;
    private final double volumeMaximoMm;

    public CalculadoraIrrigacao(
            @Value("${app.irrigacao.eficiencia-chuva:0.8}") double eficienciaChuva,
            @Value("${app.irrigacao.temperatura-alta-c:35}") double temperaturaAltaC,
            @Value("${app.irrigacao.acrescimo-temperatura:0.2}") double acrescimoTemperatura,
            @Value("${app.irrigacao.volume-maximo-mm:30}") double volumeMaximoMm
    ) {
        if (eficienciaChuva < 0 || eficienciaChuva > 1 || acrescimoTemperatura < 0 || volumeMaximoMm <= 0) {
            throw new IllegalArgumentException(
                    "app.irrigacao.*: eficiencia-chuva entre 0 e 1, acrescimo-temperatura não negativo e volume-maximo-mm positivo.");
        }
        this.eficienciaChuva = eficienciaChuva;
        this.temperaturaAltaC = temperaturaAltaC;
        this.acrescimoTemperatura = acrescimoTemperatura;
        this.volumeMaximoMm = volumeMaximoMm;
    }

    /**
     * @param umidadeAtual     média da umidade do solo na janela, ou nula se não houve leitura
     * @param previsaoAtual    se há previsão dentro da validade
     * @param chuvaPrevistaMm  chuva prevista (hoje e amanhã); nula se indisponível
     * @param temperaturaMaxima máxima prevista (hoje e amanhã); nula se indisponível
     * @param janelaUmidadeHoras janela usada na média, só para o texto
     */
    public record Entrada(
            Cultura cultura,
            Double umidadeAtual,
            OrigemUmidade origemUmidade,
            boolean previsaoAtual,
            Double chuvaPrevistaMm,
            Double temperaturaMaxima,
            int janelaUmidadeHoras
    ) {
    }

    public record Calculo(SituacaoIrrigacao situacao, Double volumeMm, String justificativa, InsumosIrrigacao insumos) {
    }

    public Calculo calcular(Entrada e) {
        Cultura cultura = e.cultura();
        List<String> faltam = new ArrayList<>();
        if (!cultura.temParametrosDeIrrigacao()) {
            faltam.add("parâmetros de irrigação da cultura " + cultura.getNome());
        }
        if (e.umidadeAtual() == null) {
            faltam.add("leitura de umidade do solo nas últimas " + e.janelaUmidadeHoras() + " h");
        }
        boolean solo = cultura.temParametrosDeIrrigacao() && e.umidadeAtual() != null;
        boolean precisaDeAgua = solo && e.umidadeAtual() < cultura.getUmidadeMinima();
        if (precisaDeAgua) {
            if (!e.previsaoAtual()) {
                faltam.add("previsão do tempo atualizada");
            } else if (e.chuvaPrevistaMm() == null) {
                faltam.add("chuva prevista");
            }
        }
        if (!faltam.isEmpty()) {
            return semDados(e, faltam);
        }

        double atual = e.umidadeAtual();
        String origem = origem(e);
        if (!precisaDeAgua) {
            String texto = "Solo adequado: umidade de %s %% (%s), acima do mínimo de %s %% para %s. Não há necessidade de irrigar."
                    .formatted(num(atual), origem, num(cultura.getUmidadeMinima()), cultura.getNome());
            return new Calculo(SituacaoIrrigacao.NAO_IRRIGAR, null, limitar(texto),
                    insumos(e, null, null, false, List.of()));
        }

        double deficit = (cultura.getUmidadeIdeal() - atual) / 100.0 * cultura.getProfundidadeRaizCm() * 10.0;
        boolean quente = e.temperaturaMaxima() != null && e.temperaturaMaxima() >= temperaturaAltaC;
        double demanda = quente ? deficit * (1 + acrescimoTemperatura) : deficit;
        double chuvaEfetiva = e.chuvaPrevistaMm() * eficienciaChuva;
        double liquido = demanda - chuvaEfetiva;

        StringBuilder texto = new StringBuilder(
                "Umidade de %s %% (%s), abaixo do mínimo de %s %% para %s (ideal %s %%). Déficit de %s mm (raízes de %d cm)."
                        .formatted(num(atual), origem, num(cultura.getUmidadeMinima()), cultura.getNome(),
                                num(cultura.getUmidadeIdeal()), num(deficit), cultura.getProfundidadeRaizCm()));
        if (quente) {
            texto.append(" Máxima prevista de %s °C: +%s %% de demanda."
                    .formatted(num(e.temperaturaMaxima()), num(acrescimoTemperatura * 100)));
        }
        texto.append(" Chuva prevista para hoje e amanhã: %s mm (efetiva %s mm)."
                .formatted(num(e.chuvaPrevistaMm()), num(chuvaEfetiva)));

        if (liquido <= 0) {
            texto.append(" A chuva prevista cobre a demanda de %s mm: aguarde a chuva e reavalie.".formatted(num(demanda)));
            return new Calculo(SituacaoIrrigacao.AGUARDAR_CHUVA, null, limitar(texto.toString()),
                    insumos(e, deficit, chuvaEfetiva, quente, List.of()));
        }
        double volume = Math.round(Math.min(liquido, volumeMaximoMm) * 10) / 10.0;
        if (liquido > volumeMaximoMm) {
            texto.append(" Volume sugerido: %s mm, limite por aplicação (faltam cerca de %s mm: parcele a irrigação)."
                    .formatted(num(volume), num(liquido - volumeMaximoMm)));
        } else {
            texto.append(" Volume sugerido: %s mm.".formatted(num(volume)));
        }
        return new Calculo(SituacaoIrrigacao.IRRIGAR, volume, limitar(texto.toString()),
                insumos(e, deficit, chuvaEfetiva, quente, List.of()));
    }

    private Calculo semDados(Entrada e, List<String> faltam) {
        String texto = "Dados insuficientes para recomendar a irrigação: faltam " + String.join("; ", faltam) + ".";
        return new Calculo(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, null, limitar(texto),
                insumos(e, null, null, false, faltam));
    }

    private static InsumosIrrigacao insumos(
            Entrada e, Double deficit, Double chuvaEfetiva, boolean ajusteTemperatura, List<String> faltam
    ) {
        Cultura c = e.cultura();
        return new InsumosIrrigacao(
                c.getNome(), c.getUmidadeMinima(), c.getUmidadeIdeal(), c.getProfundidadeRaizCm(),
                e.umidadeAtual(), e.umidadeAtual() == null ? null : e.origemUmidade(), deficit,
                e.previsaoAtual() ? e.chuvaPrevistaMm() : null, chuvaEfetiva,
                e.previsaoAtual() ? e.temperaturaMaxima() : null, ajusteTemperatura, faltam);
    }

    private static String origem(Entrada e) {
        String fonte = e.origemUmidade() == OrigemUmidade.SENSOR_TALHAO ? "sensor do talhão" : "estação da fazenda, valor de modelo";
        return "média das últimas %d h, %s".formatted(e.janelaUmidadeHoras(), fonte);
    }

    private static String limitar(String texto) {
        return texto.length() <= MAX_JUSTIFICATIVA ? texto : texto.substring(0, MAX_JUSTIFICATIVA - 1) + "…";
    }

    private static String num(double valor) {
        String texto = String.format(PT_BR, "%.1f", valor);
        return texto.endsWith(",0") ? texto.substring(0, texto.length() - 2) : texto;
    }
}
