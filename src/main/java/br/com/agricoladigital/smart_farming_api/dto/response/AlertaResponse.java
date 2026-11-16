package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.OperadorRegra;
import br.com.agricoladigital.smart_farming_api.entity.ResolucaoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;

import java.time.OffsetDateTime;

@Schema(description = "Alerta de um talhão. Instantes em UTC. atendente é quem atuou por último; atendidoEm é o primeiro atendimento.", example = "{\"id\": 41, \"talhaoId\": 3, \"fazendaId\": 1, \"fazendaNome\": \"Fazenda Santa Maria\", \"talhaoIdentificacao\": \"Talhão Norte\", \"evento\": \"SECA\", \"criticidade\": \"ALTA\", \"status\": \"ABERTO\", \"mensagem\": \"Seca: umidade média do solo de 18 % nas últimas 3 h (sensor do talhão). Regra: < 20 % (criticidade ALTA).\", \"dataHora\": \"2026-11-16T15:30:00Z\", \"atualizadoEm\": \"2026-11-16T15:30:00Z\", \"regra\": {\"id\": 3, \"operador\": \"MENOR_QUE\", \"limiar\": 20.0, \"unidade\": \"%\", \"janelaHoras\": 3}, \"leitura\": {\"id\": 981, \"dispositivo\": \"SOLO-01\", \"valor\": 18.0, \"dataHora\": \"2026-11-16T15:25:00Z\"}, \"atendente\": null, \"atendidoEm\": null, \"resolvidoEm\": null, \"resolucao\": null, \"nota\": null, \"normalizadoEm\": null}")
public record AlertaResponse(
        Long id,
        Long talhaoId,
        Long fazendaId,
        String fazendaNome,
        String talhaoIdentificacao,
        EventoAlerta evento,
        CriticidadeAlerta criticidade,
        StatusAlerta status,
        String mensagem,
        OffsetDateTime dataHora,
        OffsetDateTime atualizadoEm,
        Regra regra,
        LeituraOrigem leitura,
        Atendente atendente,
        OffsetDateTime atendidoEm,
        OffsetDateTime resolvidoEm,
        ResolucaoAlerta resolucao,
        String nota,
        OffsetDateTime normalizadoEm
) {
    @Schema(description = "Regra do nível de criticidade atual.")
    public record Regra(Long id, OperadorRegra operador, Double limiar, String unidade, Integer janelaHoras) {
    }

    @Schema(description = "Leitura que originou o alerta (alertas baseados em previsão não têm leitura).")
    public record LeituraOrigem(Long id, String dispositivo, Double valor, OffsetDateTime dataHora) {
    }

    public record Atendente(Long id, String nome) {
    }
}
