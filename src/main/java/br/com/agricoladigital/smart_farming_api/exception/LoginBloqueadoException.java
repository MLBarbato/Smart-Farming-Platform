package br.com.agricoladigital.smart_farming_api.exception;

public class LoginBloqueadoException extends RuntimeException {
    public static final String CODIGO = "LOGIN_BLOQUEADO";

    private final long segundosRestantes;

    public LoginBloqueadoException(long segundosRestantes) {
        super("Muitas tentativas de login. Tente novamente em " + minutos(segundosRestantes) + " minuto(s).");
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }

    private static long minutos(long segundos) {
        return Math.max(1, (segundos + 59) / 60);
    }
}
