package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.service.RecomendadorIrrigacao.ResumoCalculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtualizadorRecomendacoesTest {
    @Mock private FazendaRepository fazendaRepository;
    @Mock private RecomendadorIrrigacao recomendador;

    @Test
    void falhaEmUmaFazendaNaoImpedeAsDemais() {
        when(fazendaRepository.listarIds()).thenReturn(List.of(1L, 2L));
        when(recomendador.calcularFazenda(1L)).thenThrow(new IllegalStateException("erro"));
        when(recomendador.calcularFazenda(2L)).thenReturn(new ResumoCalculo(2, 1));

        assertDoesNotThrow(() -> new AtualizadorRecomendacoes(fazendaRepository, recomendador).calcularTodas());

        verify(recomendador).calcularFazenda(1L);
        verify(recomendador).calcularFazenda(2L);
    }
}
