package br.com.agricoladigital.smart_farming_api.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tipo,
        long expiraEmSegundos
) {
}
