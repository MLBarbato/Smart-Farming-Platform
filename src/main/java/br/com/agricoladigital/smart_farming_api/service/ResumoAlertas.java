package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.ResumoAlertasResponse;
import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.repository.ContagemAlerta;

import java.util.Collection;

/** Soma contagens de alertas abertos por criticidade. */
final class ResumoAlertas {
    private ResumoAlertas() {
    }

    static ResumoAlertasResponse de(Collection<ContagemAlerta> contagens) {
        int baixa = soma(contagens, CriticidadeAlerta.BAIXA);
        int media = soma(contagens, CriticidadeAlerta.MEDIA);
        int alta = soma(contagens, CriticidadeAlerta.ALTA);
        CriticidadeAlerta maior = alta > 0 ? CriticidadeAlerta.ALTA
                : media > 0 ? CriticidadeAlerta.MEDIA
                : baixa > 0 ? CriticidadeAlerta.BAIXA : null;
        return new ResumoAlertasResponse(baixa + media + alta, baixa, media, alta, maior);
    }

    static ResumoAlertasResponse somar(Collection<ResumoAlertasResponse> resumos) {
        int baixa = resumos.stream().mapToInt(ResumoAlertasResponse::baixa).sum();
        int media = resumos.stream().mapToInt(ResumoAlertasResponse::media).sum();
        int alta = resumos.stream().mapToInt(ResumoAlertasResponse::alta).sum();
        return new ResumoAlertasResponse(baixa + media + alta, baixa, media, alta,
                alta > 0 ? CriticidadeAlerta.ALTA : media > 0 ? CriticidadeAlerta.MEDIA
                        : baixa > 0 ? CriticidadeAlerta.BAIXA : null);
    }

    private static int soma(Collection<ContagemAlerta> contagens, CriticidadeAlerta criticidade) {
        return (int) contagens.stream().filter(c -> c.criticidade() == criticidade)
                .mapToLong(ContagemAlerta::total).sum();
    }
}
