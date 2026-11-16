package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;

@Schema(description = "Alertas abertos (ABERTO ou EM_ATENDIMENTO) por criticidade; maiorCriticidade é nula quando não há nenhum.", example = "{\"abertos\": 3, \"baixa\": 1, \"media\": 0, \"alta\": 2, \"maiorCriticidade\": \"ALTA\"}")
public record ResumoAlertasResponse(int abertos, int baixa, int media, int alta, CriticidadeAlerta maiorCriticidade) {
    public static final ResumoAlertasResponse NENHUM = new ResumoAlertasResponse(0, 0, 0, 0, null);
}
