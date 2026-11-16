package br.com.agricoladigital.smart_farming_api.exception;

/** O provedor de previsão não respondeu ou devolveu dados inválidos. */
public class PrevisaoIndisponivelException extends RuntimeException {
    public PrevisaoIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public PrevisaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
