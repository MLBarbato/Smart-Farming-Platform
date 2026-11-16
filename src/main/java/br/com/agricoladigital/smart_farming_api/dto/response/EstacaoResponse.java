package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.FonteEstacao;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoEstacao;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Estação meteorológica da fazenda e suas leituras mais recentes. Instantes em UTC.")
public record EstacaoResponse(
        Long id,
        String codigo,
        String modelo,
        FonteEstacao fonte,
        StatusDispositivo status,
        Integer frequenciaColetaMinutos,
        @Schema(description = "Coordenadas da fazenda, usadas na consulta ao provedor.")
        Double latitude,
        Double longitude,
        OffsetDateTime ultimaSincronizacao,
        SituacaoEstacao situacao,
        @Schema(description = "Texto de atribuição exigido pela licença dos dados; deve ser exibido ao usuário.")
        String atribuicao,
        List<LeituraResponse> leiturasAtuais
) {
}
