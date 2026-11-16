package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.IntervaloAgregacao;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Leituras de um sensor agregadas por hora ou por dia (mínimo, média e máximo). Intervalos sem leituras não aparecem. "
        + "O início de cada intervalo é um instante em UTC; os intervalos seguem o fuso pedido.",
        example = "{\"sensorId\": 12, \"variavel\": \"UMIDADE_SOLO\", \"unidade\": \"%\", \"intervalo\": \"DIA\", \"fuso\": \"America/Sao_Paulo\", "
                + "\"de\": \"2026-11-09T03:00:00Z\", \"ate\": \"2026-11-16T15:30:00Z\", \"pontos\": [{\"inicio\": \"2026-11-15T03:00:00Z\", "
                + "\"minimo\": 38.2, \"media\": 44.9, \"maximo\": 47.6, \"quantidade\": 96}]}")
public record LeituraAgregadaResponse(
        Long sensorId,
        VariavelMonitorada variavel,
        String unidade,
        IntervaloAgregacao intervalo,
        @Schema(description = "Fuso IANA usado para delimitar os intervalos (por exemplo, America/Sao_Paulo); padrão UTC.")
        String fuso,
        OffsetDateTime de,
        OffsetDateTime ate,
        List<Ponto> pontos
) {
    @Schema(description = "Agregado de um intervalo: início (UTC), mínimo, média, máximo e quantidade de leituras.")
    public record Ponto(OffsetDateTime inicio, Double minimo, Double media, Double maximo, long quantidade) {
    }
}
