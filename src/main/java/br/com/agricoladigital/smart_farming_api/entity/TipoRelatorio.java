package br.com.agricoladigital.smart_farming_api.entity;

/** Relatórios exportáveis de uma fazenda, em CSV. */
public enum TipoRelatorio {
    /** Recomendações de irrigação calculadas no período, com os insumos do cálculo. */
    IRRIGACAO("irrigacao"),
    /** Leituras horárias da estação virtual (Open-Meteo) no período. */
    CLIMA("clima"),
    /** Um resumo por sensor dos talhões: estado, leituras no período, mínimo, média e máximo. */
    SENSORES("sensores"),
    /** Alertas abertos no período, com o atendimento e o tempo até o fechamento. */
    ALERTAS("alertas"),
    /** Plantios que ocuparam o período, com colheita e produtividade. */
    PRODUTIVIDADE("produtividade");

    private final String arquivo;

    TipoRelatorio(String arquivo) {
        this.arquivo = arquivo;
    }

    /** Nome usado no arquivo baixado. */
    public String getArquivo() {
        return arquivo;
    }
}
