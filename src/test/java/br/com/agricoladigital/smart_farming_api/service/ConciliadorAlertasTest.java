package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import br.com.agricoladigital.smart_farming_api.service.ConciliadorAlertas.Acao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConciliadorAlertasTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 12, 0, 0);

    @Mock private AlertaRepository alertaRepository;
    private ConciliadorAlertas conciliador;
    private Talhao talhao;
    private RegraAlerta baixa;
    private RegraAlerta alta;

    @BeforeEach
    void preparar() {
        conciliador = new ConciliadorAlertas(alertaRepository);
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        talhao = new Talhao("A1", 10.0, null, new Fazenda("F", null, 10.0, null, null, null, null, dono));
        baixa = new RegraAlerta(EventoAlerta.SECA, CriticidadeAlerta.BAIXA, OperadorRegra.MENOR_QUE,
                VariavelMonitorada.UMIDADE_SOLO, 30.0, "%", 3, "b");
        alta = new RegraAlerta(EventoAlerta.SECA, CriticidadeAlerta.ALTA, OperadorRegra.MENOR_QUE,
                VariavelMonitorada.UMIDADE_SOLO, 20.0, "%", 3, "a");
    }

    private Alerta aberto(RegraAlerta regra) {
        return new Alerta(regra, talhao, null, AGORA.minusHours(1), "msg");
    }

    private Acao condicao(RegraAlerta regra, Alerta aberto, CriticidadeAlerta fechada) {
        return conciliador.conciliar(talhao, EventoAlerta.SECA,
                ResultadoAvaliacao.condicao(regra, "nova", null), aberto, fechada, AGORA);
    }

    @Test
    void semDadosNaoMudaNada() {
        Alerta existente = aberto(baixa);
        assertEquals(Acao.NENHUMA, conciliador.conciliar(talhao, EventoAlerta.SECA,
                ResultadoAvaliacao.SEM_DADOS, existente, null, AGORA));
        assertEquals(StatusAlerta.ABERTO, existente.getStatus());
        assertNull(existente.getNormalizadoEm());
        verifyNoInteractions(alertaRepository);
    }

    @Test
    void criaAlertaAbertoQuandoNaoHaNenhum() {
        assertEquals(Acao.CRIADO, condicao(baixa, null, null));
        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).saveAndFlush(captor.capture());
        Alerta criado = captor.getValue();
        assertEquals(StatusAlerta.ABERTO, criado.getStatus());
        assertEquals(CriticidadeAlerta.BAIXA, criado.getCriticidade());
        assertEquals(EventoAlerta.SECA, criado.getEvento());
        assertEquals("nova", criado.getMensagem());
        assertSame(talhao, criado.getTalhao());
    }

    @Test
    void naoDuplicaQuandoJaHaAlertaAbertoDaMesmaCriticidade() {
        assertEquals(Acao.NENHUMA, condicao(baixa, aberto(baixa), null));
        verifyNoInteractions(alertaRepository);
    }

    @Test
    void naoRebaixaACriticidadeQuandoACondicaoMelhora() {
        Alerta existente = aberto(alta);
        assertEquals(Acao.NENHUMA, condicao(baixa, existente, null));
        assertEquals(CriticidadeAlerta.ALTA, existente.getCriticidade());
    }

    @Test
    void sobeACriticidadeDoAlertaAbertoQuandoPiora() {
        Alerta existente = aberto(baixa);
        assertEquals(Acao.ESCALADO, condicao(alta, existente, null));
        assertEquals(CriticidadeAlerta.ALTA, existente.getCriticidade());
        assertSame(alta, existente.getRegra());
        assertEquals("nova", existente.getMensagem());
        assertEquals(AGORA, existente.getAtualizadoEm());
        verify(alertaRepository, never()).saveAndFlush(any());
    }

    @Test
    void alertaAbertoSemAtendenteFechaSozinhoQuandoNormaliza() {
        Alerta existente = aberto(baixa);
        assertEquals(Acao.RESOLVIDO_AUTOMATICAMENTE, conciliador.conciliar(talhao, EventoAlerta.SECA,
                ResultadoAvaliacao.NORMAL, existente, null, AGORA));
        assertEquals(StatusAlerta.RESOLVIDO, existente.getStatus());
        assertEquals(ResolucaoAlerta.AUTOMATICA, existente.getResolucao());
        assertEquals(AGORA, existente.getResolvidoEm());
        assertEquals(AGORA, existente.getNormalizadoEm());
        assertNull(existente.getAtendente());
    }

    @Test
    void alertaEmAtendimentoSoRegistraANormalizacaoUmaVez() {
        Alerta existente = aberto(baixa);
        org.springframework.test.util.ReflectionTestUtils.setField(existente, "status", StatusAlerta.EM_ATENDIMENTO);

        assertEquals(Acao.NORMALIZADO, conciliador.conciliar(talhao, EventoAlerta.SECA,
                ResultadoAvaliacao.NORMAL, existente, null, AGORA));
        assertEquals(StatusAlerta.EM_ATENDIMENTO, existente.getStatus());
        assertEquals(AGORA, existente.getNormalizadoEm());
        assertNull(existente.getResolvidoEm());

        assertEquals(Acao.NENHUMA, conciliador.conciliar(talhao, EventoAlerta.SECA,
                ResultadoAvaliacao.NORMAL, existente, null, AGORA.plusMinutes(15)));
        assertEquals(AGORA, existente.getNormalizadoEm());
    }

    @Test
    void condicaoQueVoltaLimpaANormalizacao() {
        Alerta existente = aberto(baixa);
        org.springframework.test.util.ReflectionTestUtils.setField(existente, "status", StatusAlerta.EM_ATENDIMENTO);
        existente.registrarNormalizacao(AGORA.minusMinutes(15));

        assertEquals(Acao.NENHUMA, condicao(baixa, existente, null));
        assertNull(existente.getNormalizadoEm());
    }

    @Test
    void normalSemAlertaAbertoNaoFazNada() {
        assertEquals(Acao.NENHUMA, conciliador.conciliar(talhao, EventoAlerta.SECA,
                ResultadoAvaliacao.NORMAL, null, null, AGORA));
        verifyNoInteractions(alertaRepository);
    }

    @Test
    void adiaReaberturaDentroDoPeriodoDeEsperaSeACriticidadeNaoForMaior() {
        assertEquals(Acao.REABERTURA_ADIADA, condicao(baixa, null, CriticidadeAlerta.BAIXA));
        assertEquals(Acao.REABERTURA_ADIADA, condicao(baixa, null, CriticidadeAlerta.ALTA));
        verify(alertaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reabreDentroDoPeriodoDeEsperaSeACriticidadeForMaior() {
        assertEquals(Acao.CRIADO, condicao(alta, null, CriticidadeAlerta.BAIXA));
        verify(alertaRepository).saveAndFlush(any(Alerta.class));
    }
}
