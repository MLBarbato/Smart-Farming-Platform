package br.com.agricoladigital.smart_farming_api.security;

import br.com.agricoladigital.smart_farming_api.exception.LoginBloqueadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LimiteTentativasLoginTest {
    private static final String IP = "203.0.113.7";

    private static class RelogioMutavel extends Clock {
        private Instant agora = Instant.parse("2026-01-01T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private RelogioMutavel relogio;
    private LimiteTentativasLogin limite;

    @BeforeEach
    void criar() {
        relogio = new RelogioMutavel();
        limite = new LimiteTentativasLogin(3, 5, Duration.ofMinutes(15), Duration.ofMinutes(10), relogio);
    }

    @Test
    void bloqueiaNaTentativaSeguinteAoLimite() {
        for (int i = 0; i < 2; i++) {
            limite.registrarFalha("ana@example.com", IP);
            assertDoesNotThrow(() -> limite.verificar("ana@example.com", IP));
        }
        limite.registrarFalha("ana@example.com", IP);

        LoginBloqueadoException erro = assertThrows(LoginBloqueadoException.class,
                () -> limite.verificar("ana@example.com", IP));
        assertEquals(600, erro.getSegundosRestantes());
    }

    @Test
    void bloqueioExpiraEContadorRecomeca() {
        limite = new LimiteTentativasLogin(3, 50, Duration.ofMinutes(15), Duration.ofMinutes(10), relogio);
        for (int i = 0; i < 3; i++) {
            limite.registrarFalha("ana@example.com", IP);
        }
        relogio.avancar(Duration.ofMinutes(10));

        assertDoesNotThrow(() -> limite.verificar("ana@example.com", IP));
        limite.registrarFalha("ana@example.com", IP);
        limite.registrarFalha("ana@example.com", IP);
        assertDoesNotThrow(() -> limite.verificar("ana@example.com", IP));
    }

    @Test
    void falhasForaDaJanelaNaoSeAcumulam() {
        limite.registrarFalha("ana@example.com", IP);
        limite.registrarFalha("ana@example.com", IP);
        relogio.avancar(Duration.ofMinutes(16));
        limite.registrarFalha("ana@example.com", IP);

        assertDoesNotThrow(() -> limite.verificar("ana@example.com", IP));
    }

    @Test
    void sucessoZeraContadorDoParEmailIp() {
        limite.registrarFalha("ana@example.com", IP);
        limite.registrarFalha("ana@example.com", IP);
        limite.registrarSucesso("ana@example.com", IP);
        limite.registrarFalha("ana@example.com", IP);
        limite.registrarFalha("ana@example.com", IP);

        assertDoesNotThrow(() -> limite.verificar("ana@example.com", IP));
    }

    @Test
    void outroIpNaoEBloqueadoPelasFalhasDeUmIpParaAMesmaConta() {
        for (int i = 0; i < 3; i++) {
            limite.registrarFalha("ana@example.com", IP);
        }

        assertThrows(LoginBloqueadoException.class, () -> limite.verificar("ana@example.com", IP));
        assertDoesNotThrow(() -> limite.verificar("ana@example.com", "198.51.100.9"));
    }

    @Test
    void ipQueTestaMuitosEmailsEBloqueadoPeloLimiteDoIp() {
        for (int i = 0; i < 5; i++) {
            limite.registrarFalha("usuario" + i + "@example.com", IP);
        }

        assertThrows(LoginBloqueadoException.class, () -> limite.verificar("novo@example.com", IP));
        assertDoesNotThrow(() -> limite.verificar("novo@example.com", "198.51.100.9"));
    }

    @Test
    void configuracaoInvalidaEhRejeitada() {
        assertThrows(IllegalArgumentException.class,
                () -> new LimiteTentativasLogin(0, 5, Duration.ofMinutes(1), Duration.ofMinutes(1), relogio));
        assertThrows(IllegalArgumentException.class,
                () -> new LimiteTentativasLogin(3, 5, Duration.ZERO, Duration.ofMinutes(1), relogio));
    }
}
