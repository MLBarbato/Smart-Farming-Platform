package br.com.agricoladigital.smart_farming_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Par de tokens (accessToken JWT de 15 min e refreshToken opaco, de 7 dias e rotativo) e o usuário autenticado.", example = "{\"accessToken\": \"eyJhbGciOi...\", \"refreshToken\": \"x7Qe...\", \"tipo\": \"Bearer\", \"expiraEmSegundos\": 900, \"usuario\": {\"id\": 1, \"nome\": \"Ana Silva\", \"email\": \"ana@example.com\", \"perfil\": \"AGRICULTOR\", \"ativo\": true}}")
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tipo,
        long expiraEmSegundos,
        @Schema(description = "Usuário autenticado (id, nome, e-mail e perfil), para a interface não precisar decodificar o JWT.")
        UsuarioResponse usuario
) {
}
