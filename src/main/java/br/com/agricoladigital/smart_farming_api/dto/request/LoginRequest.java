package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciais de acesso.", example = "{\"email\": \"ana@example.com\", \"senha\": \"senha-segura-123\"}")
public record LoginRequest(
        @NotBlank @Size(max = 150) String email,
        @NotBlank @Size(max = 72) String senha
) {
}
