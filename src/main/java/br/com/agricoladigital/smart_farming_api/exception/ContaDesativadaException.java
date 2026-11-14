package br.com.agricoladigital.smart_farming_api.exception;

import org.springframework.security.core.AuthenticationException;

public class ContaDesativadaException extends AuthenticationException {
    public static final String CODIGO = "USUARIO_DESATIVADO";

    public ContaDesativadaException() {
        super("Conta desativada. Entre em contato com o administrador.");
    }
}
