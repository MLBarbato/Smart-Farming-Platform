package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Talhões em cultivo por situação da recomendação de irrigação mais recente.", example = "{\"irrigar\": 2, \"aguardarChuva\": 0, \"naoIrrigar\": 1, \"semDadosSuficientes\": 0}")
public record ResumoIrrigacaoResponse(int irrigar, int aguardarChuva, int naoIrrigar, int semDadosSuficientes) {
    public static final ResumoIrrigacaoResponse NENHUM = new ResumoIrrigacaoResponse(0, 0, 0, 0);
}
