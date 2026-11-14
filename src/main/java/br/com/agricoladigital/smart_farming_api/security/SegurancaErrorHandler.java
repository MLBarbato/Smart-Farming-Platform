package br.com.agricoladigital.smart_farming_api.security;

import br.com.agricoladigital.smart_farming_api.dto.response.ErroResponse;
import br.com.agricoladigital.smart_farming_api.exception.ContaDesativadaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/** Gera respostas 401/403 no mesmo formato JSON dos demais erros da API. */
@Component
public class SegurancaErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public SegurancaErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        if (exception instanceof ContaDesativadaException) {
            escrever(response, HttpStatus.FORBIDDEN, exception.getMessage(), ContaDesativadaException.CODIGO);
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escrever(response, HttpStatus.UNAUTHORIZED, "Autenticação necessária ou token inválido.", null);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, "Acesso negado.", null);
    }

    private void escrever(
            HttpServletResponse response,
            HttpStatus status,
            String mensagem,
            String codigo
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErroResponse body = new ErroResponse(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), mensagem, Map.of(), codigo
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
