package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoIrrigacao;

import java.time.OffsetDateTime;

@Schema(description = "Recomendação de irrigação mais recente de um talhão, em resumo.", example = "{\"id\": 12, \"situacao\": \"IRRIGAR\", \"volumeRecomendadoMm\": 26.0, \"vencida\": false, \"dataHora\": \"2026-11-16T15:30:00Z\"}")
public record RecomendacaoAtualResponse(
        Long id,
        SituacaoIrrigacao situacao,
        Double volumeRecomendadoMm,
        boolean vencida,
        OffsetDateTime dataHora
) {
}
