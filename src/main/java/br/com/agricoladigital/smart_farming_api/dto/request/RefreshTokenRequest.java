package br.com.agricoladigital.smart_farming_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank @Size(max = 200) String refreshToken
) {
}
