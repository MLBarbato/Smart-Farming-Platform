package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;

@Schema(description = "Usuário (a senha nunca é devolvida).", example = "{\"id\": 1, \"nome\": \"Ana Silva\", \"email\": \"ana@example.com\", \"perfil\": \"AGRICULTOR\", \"ativo\": true}")
public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        PerfilUsuario perfil,
        Boolean ativo
) {
}
