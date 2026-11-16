package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.SituacaoPrevisao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Previsão diária da fazenda, com a idade dos dados. Temperaturas em °C e precipitação em mm. */
public record PrevisaoFazendaResponse(
        Long fazendaId,
        SituacaoPrevisao situacao,
        String fonte,
        /** Texto de atribuição exigido pela licença dos dados; deve ser exibido ao usuário. */
        String atribuicao,
        /** Instante (UTC) da última consulta bem-sucedida ao provedor; nulo se nunca houve. */
        OffsetDateTime consultadaEm,
        /** Horas de validade após as quais a previsão passa a {@code DESATUALIZADA}. */
        int validadeHoras,
        List<Dia> dias
) {
    public record Dia(
            LocalDate data,
            Double temperaturaMedia,
            Double temperaturaMinima,
            Double temperaturaMaxima,
            Double precipitacaoMm,
            Integer probabilidadePrecipitacao
    ) {
    }
}
