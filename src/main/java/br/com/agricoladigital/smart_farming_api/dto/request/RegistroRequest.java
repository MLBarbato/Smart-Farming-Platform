package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Cadastro público: o perfil criado é sempre AGRICULTOR.", example = "{\"nome\": \"Ana Silva\", \"email\": \"ana@example.com\", \"senha\": \"senha-segura-123\"}")
public record RegistroRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 72) String senha
) {
}
