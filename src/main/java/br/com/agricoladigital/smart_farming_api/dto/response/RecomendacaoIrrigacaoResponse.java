package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.OrigemUmidade;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoIrrigacao;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Recomendação de irrigação de um talhão com os insumos do cálculo. Apoia a decisão; os parâmetros são de demonstração.", example = "{\"id\": 12, \"talhaoId\": 3, \"talhaoIdentificacao\": \"Talhão Norte\", \"situacao\": \"IRRIGAR\", \"volumeRecomendadoMm\": 26.0, \"justificativa\": \"Umidade de 28 % (média das últimas 3 h, sensor do talhão), abaixo do mínimo de 30 % para Soja (ideal 38 %). Déficit de 30 mm (raízes de 30 cm). Chuva prevista para hoje e amanhã: 5 mm (efetiva 4 mm). Volume sugerido: 26 mm.\", \"dataHora\": \"2026-11-16T15:30:00Z\", \"validoAte\": \"2026-11-16T21:30:00Z\", \"vencida\": false, \"cultura\": {\"nome\": \"Soja\", \"umidadeMinima\": 30.0, \"umidadeIdeal\": 38.0, \"profundidadeRaizCm\": 30}, \"umidade\": {\"atual\": 28.0, \"origem\": \"SENSOR_TALHAO\"}, \"deficitMm\": 30.0, \"chuva\": {\"previstaMm\": 5.0, \"efetivaMm\": 4.0}, \"temperaturaMaximaPrevista\": 31.0, \"ajusteTemperatura\": false, \"previsao\": {\"fonte\": \"OPEN_METEO\", \"consultadaEm\": \"2026-11-16T14:00:00Z\", \"atribuicao\": \"Dados meteorológicos por Open-Meteo.com (https://open-meteo.com), licença CC BY 4.0.\"}, \"dadosFaltantes\": []}")
public record RecomendacaoIrrigacaoResponse(
        Long id,
        Long talhaoId,
        String talhaoIdentificacao,
        SituacaoIrrigacao situacao,
        @Schema(description = "Lâmina sugerida em mm; só quando a situação é IRRIGAR.")
        Double volumeRecomendadoMm,
        String justificativa,
        OffsetDateTime dataHora,
        OffsetDateTime validoAte,
        @Schema(description = "Se a validade passou (o próximo cálculo do job ainda não ocorreu ou não mudou o resultado).")
        boolean vencida,
        Cultura cultura,
        Umidade umidade,
        Double deficitMm,
        Chuva chuva,
        Double temperaturaMaximaPrevista,
        boolean ajusteTemperatura,
        @Schema(description = "Previsão usada; nula se não havia previsão atual.")
        Previsao previsao,
        @Schema(description = "O que faltou para recomendar (só em SEM_DADOS_SUFICIENTES).")
        List<String> dadosFaltantes
) {
    public record Cultura(String nome, Double umidadeMinima, Double umidadeIdeal, Integer profundidadeRaizCm) {
    }

    public record Umidade(Double atual, OrigemUmidade origem) {
    }

    public record Chuva(Double previstaMm, Double efetivaMm) {
    }

    public record Previsao(String fonte, OffsetDateTime consultadaEm, String atribuicao) {
    }
}
