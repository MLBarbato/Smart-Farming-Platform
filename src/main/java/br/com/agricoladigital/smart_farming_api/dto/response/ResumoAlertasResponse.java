package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;

/**
 * Alertas abertos (ABERTO ou EM_ATENDIMENTO). {@code maiorCriticidade} é nula quando não há nenhum;
 * serve para colorir o talhão ou o cartão.
 */
public record ResumoAlertasResponse(int abertos, int baixa, int media, int alta, CriticidadeAlerta maiorCriticidade) {
    public static final ResumoAlertasResponse NENHUM = new ResumoAlertasResponse(0, 0, 0, 0, null);
}
