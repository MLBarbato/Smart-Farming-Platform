package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Formato padrão de erro da API. codigo só aparece em erros especiais (USUARIO_DESATIVADO, LOGIN_BLOQUEADO).", example = "{\"dataHora\": \"2026-11-16T15:30:00\", \"status\": 409, \"erro\": \"Conflict\", \"mensagem\": \"Um alerta RESOLVIDO não pode passar para ABERTO.\", \"detalhes\": {}}")
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
