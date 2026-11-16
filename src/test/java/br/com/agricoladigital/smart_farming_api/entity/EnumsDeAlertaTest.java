package br.com.agricoladigital.smart_farming_api.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EnumsDeAlertaTest {
    @Test
    void transicoesDoAtendimentoSeguemODiagrama() {
        assertEquals(Set.of(StatusAlerta.EM_ATENDIMENTO, StatusAlerta.RESOLVIDO, StatusAlerta.DISPENSADO),
                StatusAlerta.ABERTO.proximos());
        assertEquals(Set.of(StatusAlerta.RESOLVIDO, StatusAlerta.DISPENSADO), StatusAlerta.EM_ATENDIMENTO.proximos());
        assertTrue(StatusAlerta.RESOLVIDO.proximos().isEmpty());
        assertTrue(StatusAlerta.DISPENSADO.proximos().isEmpty());
    }

    @Test
    void soAbertoEEmAtendimentoContamComoAbertos() {
        assertTrue(StatusAlerta.ABERTO.aberto());
        assertTrue(StatusAlerta.EM_ATENDIMENTO.aberto());
        assertFalse(StatusAlerta.RESOLVIDO.aberto());
        assertFalse(StatusAlerta.DISPENSADO.aberto());
    }

    @Test
    void operadoresComparamNosLimites() {
        assertFalse(OperadorRegra.MENOR_QUE.satisfeito(30, 30));
        assertTrue(OperadorRegra.MENOR_OU_IGUAL.satisfeito(30, 30));
        assertFalse(OperadorRegra.MAIOR_QUE.satisfeito(30, 30));
        assertTrue(OperadorRegra.MAIOR_OU_IGUAL.satisfeito(30, 30));
        assertTrue(OperadorRegra.MENOR_QUE.satisfeito(29.9, 30));
        assertTrue(OperadorRegra.MAIOR_QUE.satisfeito(30.1, 30));
    }

    @Test
    void direcaoDoPiorValorDependeDoOperador() {
        assertTrue(OperadorRegra.MENOR_QUE.piorQuandoMenor());
        assertTrue(OperadorRegra.MENOR_OU_IGUAL.piorQuandoMenor());
        assertFalse(OperadorRegra.MAIOR_QUE.piorQuandoMenor());
        assertFalse(OperadorRegra.MAIOR_OU_IGUAL.piorQuandoMenor());
    }

    @Test
    void soFalhaDeSensorNaoUsaJanela() {
        assertTrue(EventoAlerta.SECA.usaJanela());
        assertTrue(EventoAlerta.EXCESSO_PRECIPITACAO.usaJanela());
        assertTrue(EventoAlerta.RISCO_GEADA.usaJanela());
        assertFalse(EventoAlerta.FALHA_SENSOR.usaJanela());
    }
}
