package br.com.agricoladigital.smart_farming_api.exception;

import br.com.agricoladigital.smart_farming_api.dto.response.ErroResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> tratarNaoEncontrado(RecursoNaoEncontradoException exception) {
        return resposta(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> tratarRegraDeNegocio(RegraDeNegocioException exception) {
        return resposta(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponse> tratarConflito(ConflitoException exception) {
        return resposta(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErroResponse> tratarConcorrencia(ObjectOptimisticLockingFailureException exception) {
        return resposta(HttpStatus.CONFLICT,
                "O registro foi alterado por outra operação ao mesmo tempo; consulte-o e tente de novo.", Map.of());
    }

    @ExceptionHandler(SenhaAtualInvalidaException.class)
    public ResponseEntity<ErroResponse> tratarSenhaAtualInvalida(SenhaAtualInvalidaException exception) {
        return resposta(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler({CredenciaisInvalidasException.class, RefreshTokenInvalidoException.class})
    public ResponseEntity<ErroResponse> tratarNaoAutenticado(RuntimeException exception) {
        return resposta(HttpStatus.UNAUTHORIZED, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ContaDesativadaException.class)
    public ResponseEntity<ErroResponse> tratarContaDesativada(ContaDesativadaException exception) {
        return resposta(HttpStatus.FORBIDDEN, exception.getMessage(), Map.of(), ContaDesativadaException.CODIGO);
    }

    @ExceptionHandler(LoginBloqueadoException.class)
    public ResponseEntity<ErroResponse> tratarLoginBloqueado(LoginBloqueadoException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getSegundosRestantes()))
                .body(corpo(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), Map.of(),
                        LoginBloqueadoException.CODIGO));
    }

    @ExceptionHandler(PrevisaoIndisponivelException.class)
    public ResponseEntity<ErroResponse> tratarPrevisaoIndisponivel(PrevisaoIndisponivelException exception) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE,
                "O serviço de previsão do tempo está indisponível no momento. Tente novamente mais tarde.", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> tratarAcessoNegado(AccessDeniedException exception) {
        return resposta(HttpStatus.FORBIDDEN, "Acesso negado.", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> tratarTipoInvalido(MethodArgumentTypeMismatchException exception) {
        return resposta(
                HttpStatus.BAD_REQUEST,
                "O valor informado para '" + exception.getName() + "' é inválido.",
                Map.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> tratarIntegridade(DataIntegrityViolationException exception) {
        return resposta(
                HttpStatus.CONFLICT,
                "A operação viola uma restrição de integridade ou há registros dependentes.",
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> tratarValidacao(MethodArgumentNotValidException exception) {
        Map<String, String> detalhes = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                detalhes.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return resposta(HttpStatus.BAD_REQUEST, "Revise os campos informados.", detalhes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> tratarCorpoInvalido(HttpMessageNotReadableException exception) {
        return resposta(
                HttpStatus.BAD_REQUEST,
                "O corpo da requisição está inválido. Verifique os tipos e os valores dos campos.",
                Map.of()
        );
    }

    private ResponseEntity<ErroResponse> resposta(
            HttpStatus status,
            String mensagem,
            Map<String, String> detalhes
    ) {
        return resposta(status, mensagem, detalhes, null);
    }

    private ResponseEntity<ErroResponse> resposta(
            HttpStatus status,
            String mensagem,
            Map<String, String> detalhes,
            String codigo
    ) {
        return ResponseEntity.status(status).body(corpo(status, mensagem, detalhes, codigo));
    }

    private ErroResponse corpo(HttpStatus status, String mensagem, Map<String, String> detalhes, String codigo) {
        return new ErroResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                detalhes,
                codigo
        );
    }
}
