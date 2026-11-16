package br.com.agricoladigital.smart_farming_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Troca de senha do próprio usuário.", example = "{\"senhaAtual\": \"senha-antiga-123\", \"novaSenha\": \"nova-senha-456\"}")
public record AlterarSenhaRequest(
        @NotBlank @Size(max = 72) String senhaAtual,
        @NotBlank @Size(min = 8, max = 72) String novaSenha
) {
}
