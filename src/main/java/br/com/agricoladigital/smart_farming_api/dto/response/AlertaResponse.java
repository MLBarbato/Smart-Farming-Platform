package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.OperadorRegra;
import br.com.agricoladigital.smart_farming_api.entity.ResolucaoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;

import java.time.OffsetDateTime;

/**
 * Alerta de um talhão. Instantes em UTC. {@code atendente} é quem atuou por último;
 * {@code atendidoEm} é o primeiro atendimento. {@code normalizadoEm} indica que a condição
 * deixou de existir (o alerta segue com o responsável até ser fechado).
 */
public record AlertaResponse(
        Long id,
        Long talhaoId,
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
    /** Regra do nível de criticidade atual. */
    public record Regra(Long id, OperadorRegra operador, Double limiar, String unidade, Integer janelaHoras) {
    }

    /** Leitura que originou o alerta (alertas baseados em previsão não têm leitura). */
    public record LeituraOrigem(Long id, String dispositivo, Double valor, OffsetDateTime dataHora) {
    }

    public record Atendente(Long id, String nome) {
    }
}
