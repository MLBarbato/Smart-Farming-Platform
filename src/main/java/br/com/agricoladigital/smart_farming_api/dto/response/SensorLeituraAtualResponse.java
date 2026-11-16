package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Um sensor do talhão com a leitura mais recente (nula se ainda não houver nenhuma).")
public record SensorLeituraAtualResponse(SensorResponse sensor, LeituraResponse ultimaLeitura) {
}
