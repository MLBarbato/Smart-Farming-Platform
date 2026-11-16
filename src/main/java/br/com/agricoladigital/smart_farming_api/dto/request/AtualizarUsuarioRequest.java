package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados editáveis de um usuário. Só o administrador altera o perfil.", example = "{\"nome\": \"Ana Silva\", \"email\": \"ana@example.com\", \"perfil\": \"AGRICULTOR\"}")
public record AtualizarUsuarioRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull PerfilUsuario perfil
) {
}
