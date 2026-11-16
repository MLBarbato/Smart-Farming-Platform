package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.RecomendacaoAtualResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoIrrigacaoResponse;
import br.com.agricoladigital.smart_farming_api.entity.RecomendacaoIrrigacao;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoIrrigacao;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

/** Contagens e resumos das recomendações de irrigação para o dashboard e o mapa. */
final class ResumoIrrigacao {
    private ResumoIrrigacao() {
    }

    static ResumoIrrigacaoResponse de(Collection<RecomendacaoIrrigacao> recomendacoes) {
        return new ResumoIrrigacaoResponse(
                contar(recomendacoes, SituacaoIrrigacao.IRRIGAR),
                contar(recomendacoes, SituacaoIrrigacao.AGUARDAR_CHUVA),
                contar(recomendacoes, SituacaoIrrigacao.NAO_IRRIGAR),
                contar(recomendacoes, SituacaoIrrigacao.SEM_DADOS_SUFICIENTES));
    }

    static ResumoIrrigacaoResponse somar(List<ResumoIrrigacaoResponse> resumos) {
        return new ResumoIrrigacaoResponse(
                resumos.stream().mapToInt(ResumoIrrigacaoResponse::irrigar).sum(),
                resumos.stream().mapToInt(ResumoIrrigacaoResponse::aguardarChuva).sum(),
                resumos.stream().mapToInt(ResumoIrrigacaoResponse::naoIrrigar).sum(),
                resumos.stream().mapToInt(ResumoIrrigacaoResponse::semDadosSuficientes).sum());
    }

    static RecomendacaoAtualResponse atual(RecomendacaoIrrigacao r, LocalDateTime agora) {
        return new RecomendacaoAtualResponse(r.getId(), r.getSituacao(), r.getVolumeRecomendadoMm(),
                !agora.isBefore(r.getValidoAte()), r.getDataHora().atOffset(ZoneOffset.UTC));
    }

    private static int contar(Collection<RecomendacaoIrrigacao> recomendacoes, SituacaoIrrigacao situacao) {
        return (int) recomendacoes.stream().filter(r -> r.getSituacao() == situacao).count();
    }
}
