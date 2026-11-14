package br.com.agricoladigital.smart_farming_api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(
        LocalDateTime dataHora,
        int status,
        String erro,
        String mensagem,
        Map<String, String> detalhes,
        String codigo
) {
    public ErroResponse(
            LocalDateTime dataHora,
            int status,
            String erro,
            String mensagem,
            Map<String, String> detalhes
    ) {
        this(dataHora, status, erro, mensagem, detalhes, null);
    }
}
