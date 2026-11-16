package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.Leitura;
import br.com.agricoladigital.smart_farming_api.entity.RegraAlerta;

/**
 * Resultado da avaliação de um evento em um talhão.
 *
 * @param tipo     {@code CONDICAO}: alguma regra foi satisfeita; {@code NORMAL}: há dados e nenhuma regra
 *                 foi satisfeita; {@code SEM_DADOS}: não foi possível avaliar (nada é criado nem normalizado)
 * @param regra    regra mais severa satisfeita (só em {@code CONDICAO})
 * @param mensagem texto do alerta (só em {@code CONDICAO})
 * @param leitura  leitura que originou a condição, quando houver
 */
public record ResultadoAvaliacao(Tipo tipo, RegraAlerta regra, String mensagem, Leitura leitura) {
    public enum Tipo { SEM_DADOS, NORMAL, CONDICAO }

    public static final ResultadoAvaliacao SEM_DADOS = new ResultadoAvaliacao(Tipo.SEM_DADOS, null, null, null);
    public static final ResultadoAvaliacao NORMAL = new ResultadoAvaliacao(Tipo.NORMAL, null, null, null);

    public static ResultadoAvaliacao condicao(RegraAlerta regra, String mensagem, Leitura leitura) {
        return new ResultadoAvaliacao(Tipo.CONDICAO, regra, mensagem, leitura);
    }
}
