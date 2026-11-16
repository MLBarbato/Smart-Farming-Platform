package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Criação de usuário por um administrador, com o perfil escolhido.", example = "{\"nome\": \"Carlos Souza\", \"email\": \"carlos@example.com\", \"senha\": \"senha-segura-123\", \"perfil\": \"AGRICULTOR\"}")
public record CriarUsuarioRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 72) String senha,
        @NotNull PerfilUsuario perfil
) {
}
