package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Visão geral das fazendas do usuário (ou de todas, para o administrador).")
public record ResumoGeralResponse(Totais totais, List<FazendaCard> fazendas, int criterioSemLeituraRecenteMinutos) {

    public record Totais(
            int fazendas,
            int talhoes,
            double areaTotalHectares,
            double areaEmCultivoHectares,
            int sensores,
            int sensoresAtivos,
            int sensoresSemLeituraRecente,
            int plantiosEmCultivo,
            ResumoAlertasResponse alertas,
            ResumoIrrigacaoResponse irrigacao
    ) {
    }

    public record FazendaCard(
            Long id,
            String nome,
            String cidade,
            String estado,
            Double areaTotal,
            int talhoes,
            int sensores,
            int sensoresAtivos,
            int sensoresSemLeituraRecente,
            int plantiosEmCultivo,
            List<String> culturasEmCultivo,
            ResumoAlertasResponse alertas,
            ResumoIrrigacaoResponse irrigacao
    ) {
    }
}
