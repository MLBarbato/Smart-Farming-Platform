package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtualizadorPrevisaoTest {
    @Mock private FazendaRepository fazendaRepository;
    @Mock private PrevisaoService previsaoService;

    private Fazenda fazenda(long id) {
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        Fazenda fazenda = new Fazenda("F" + id, null, 10.0, -17.0, -50.0, null, null, dono);
        ReflectionTestUtils.setField(fazenda, "id", id);
        return fazenda;
    }

    @Test
    void falhaEmUmaFazendaNaoImpedeAsDemais() {
        Fazenda a = fazenda(1);
        Fazenda b = fazenda(2);
        when(fazendaRepository.findAllByLatitudeIsNotNullAndLongitudeIsNotNull()).thenReturn(List.of(a, b));
        doThrow(new PrevisaoIndisponivelException("fora do ar")).when(previsaoService).atualizar(a);

        assertDoesNotThrow(() -> new AtualizadorPrevisao(fazendaRepository, previsaoService).atualizarTodas());

        verify(previsaoService).atualizar(a);
        verify(previsaoService).atualizar(b);
    }

    @Test
    void semFazendasComCoordenadasNaoChamaOProvedor() {
        when(fazendaRepository.findAllByLatitudeIsNotNullAndLongitudeIsNotNull()).thenReturn(List.of());

        new AtualizadorPrevisao(fazendaRepository, previsaoService).atualizarTodas();

        verifyNoInteractions(previsaoService);
    }
}
