package br.com.agricoladigital.smart_farming_api.security;

import br.com.agricoladigital.smart_farming_api.exception.LoginBloqueadoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas de login malsucedidas, em memória (reiniciar a aplicação zera os contadores
 * e cada instância conta separadamente).
 *
 * <p>Dois contadores independentes: um por par e-mail + IP (limite menor, protege uma conta
 * contra adivinhação sem permitir que um terceiro a bloqueie de outro IP) e um por IP (limite
 * maior, contém quem testa muitos e-mails). Ao atingir o limite, a chave fica bloqueada por
 * um período fixo. Um login bem-sucedido zera apenas o contador do par e-mail + IP.
 */
@Component
public class LimiteTentativasLogin {
    private static final int LIMITE_PARA_LIMPEZA = 10_000;

    private record Estado(int falhas, Instant inicioJanela, Instant bloqueadoAte) {
    }

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();
    private final int maxPorConta;
    private final int maxPorIp;
    private final Duration janela;
    private final Duration bloqueio;
    private final Clock clock;

    @Autowired
    public LimiteTentativasLogin(
            @Value("${app.login.max-tentativas:5}") int maxPorConta,
            @Value("${app.login.max-tentativas-por-ip:20}") int maxPorIp,
            @Value("${app.login.janela-minutos:15}") long janelaMinutos,
            @Value("${app.login.bloqueio-minutos:15}") long bloqueioMinutos
    ) {
        this(maxPorConta, maxPorIp, Duration.ofMinutes(janelaMinutos), Duration.ofMinutes(bloqueioMinutos),
                Clock.systemUTC());
    }

    public LimiteTentativasLogin(int maxPorConta, int maxPorIp, Duration janela, Duration bloqueio, Clock clock) {
        if (maxPorConta < 1 || maxPorIp < 1 || janela.isZero() || janela.isNegative()
                || bloqueio.isZero() || bloqueio.isNegative()) {
            throw new IllegalArgumentException("Configuração app.login.* inválida: valores devem ser positivos.");
        }
        this.maxPorConta = maxPorConta;
        this.maxPorIp = maxPorIp;
        this.janela = janela;
        this.bloqueio = bloqueio;
        this.clock = clock;
    }

    /** Lança {@link LoginBloqueadoException} se o par e-mail + IP ou o IP estiver bloqueado. */
    public void verificar(String email, String ip) {
        Instant agora = clock.instant();
        long restante = Math.max(segundosRestantes(chaveConta(email, ip), agora), segundosRestantes(chaveIp(ip), agora));
        if (restante > 0) {
            throw new LoginBloqueadoException(restante);
        }
    }

    public void registrarFalha(String email, String ip) {
        Instant agora = clock.instant();
        if (estados.size() > LIMITE_PARA_LIMPEZA) {
            estados.entrySet().removeIf(entrada -> expirado(entrada.getValue(), agora));
        }
        registrarFalha(chaveConta(email, ip), maxPorConta, agora);
        registrarFalha(chaveIp(ip), maxPorIp, agora);
    }

    public void registrarSucesso(String email, String ip) {
        estados.remove(chaveConta(email, ip));
    }

    private void registrarFalha(String chave, int limite, Instant agora) {
        estados.compute(chave, (k, atual) -> {
            if (atual == null || expirado(atual, agora)) {
                atual = new Estado(0, agora, null);
            }
            int falhas = atual.falhas() + 1;
            Instant bloqueadoAte = falhas >= limite ? agora.plus(bloqueio) : atual.bloqueadoAte();
            return new Estado(falhas, atual.inicioJanela(), bloqueadoAte);
        });
    }

    private long segundosRestantes(String chave, Instant agora) {
        Estado estado = estados.get(chave);
        if (estado == null || estado.bloqueadoAte() == null || !estado.bloqueadoAte().isAfter(agora)) {
            return 0;
        }
        return Math.max(1, Duration.between(agora, estado.bloqueadoAte()).toSeconds());
    }

    private boolean expirado(Estado estado, Instant agora) {
        if (estado.bloqueadoAte() != null) {
            return !estado.bloqueadoAte().isAfter(agora);
        }
        return !estado.inicioJanela().plus(janela).isAfter(agora);
    }

    private static String chaveConta(String email, String ip) {
        return "conta:" + email + "|" + ip;
    }

    private static String chaveIp(String ip) {
        return "ip:" + ip;
    }
}
