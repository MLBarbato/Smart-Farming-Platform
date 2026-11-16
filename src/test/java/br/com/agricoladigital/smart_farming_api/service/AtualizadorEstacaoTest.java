package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtualizadorEstacaoTest {
    @Mock private EstacaoMeteorologicaRepository estacaoRepository;
    @Mock private EstacaoSincronizador sincronizador;

    private EstacaoMeteorologica estacao() {
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        Fazenda fazenda = new Fazenda("F", null, 10.0, -17.0, -50.0, null, null, dono);
        return new EstacaoMeteorologica(
                new Dispositivo("E", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda), "m", 60, FonteEstacao.OPEN_METEO);
    }

    @Test
    void falhaEmUmaEstacaoNaoImpedeAsDemais() {
        EstacaoMeteorologica a = estacao();
        EstacaoMeteorologica b = estacao();
        when(estacaoRepository.listarPorStatus(StatusDispositivo.ATIVO)).thenReturn(List.of(a, b));
        when(sincronizador.sincronizar(a)).thenThrow(new PrevisaoIndisponivelException("fora do ar"));

        assertDoesNotThrow(() -> new AtualizadorEstacao(estacaoRepository, sincronizador).sincronizarTodas());

        verify(sincronizador).sincronizar(a);
        verify(sincronizador).sincronizar(b);
    }
}
