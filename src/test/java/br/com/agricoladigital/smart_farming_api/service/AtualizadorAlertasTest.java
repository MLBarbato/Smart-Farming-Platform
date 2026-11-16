package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.service.ConciliadorAlertas.Acao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtualizadorAlertasTest {
    @Mock private FazendaRepository fazendaRepository;
    @Mock private AvaliadorAlertas avaliador;

    @Test
    void falhaEmUmaFazendaNaoImpedeAsDemais() {
        when(fazendaRepository.listarIds()).thenReturn(List.of(1L, 2L));
        when(avaliador.avaliarFazenda(1L)).thenThrow(new IllegalStateException("erro"));
        when(avaliador.avaliarFazenda(2L)).thenReturn(Map.of(Acao.CRIADO, 1));

        assertDoesNotThrow(() -> new AtualizadorAlertas(fazendaRepository, avaliador).avaliarTodas());

        verify(avaliador).avaliarFazenda(1L);
        verify(avaliador).avaliarFazenda(2L);
    }
}
