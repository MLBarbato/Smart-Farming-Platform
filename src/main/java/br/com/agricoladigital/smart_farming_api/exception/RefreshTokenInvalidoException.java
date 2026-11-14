package br.com.agricoladigital.smart_farming_api.exception;

public class RefreshTokenInvalidoException extends RuntimeException {
    public RefreshTokenInvalidoException() {
        super("Refresh token inválido ou expirado. Faça login novamente.");
    }
}
