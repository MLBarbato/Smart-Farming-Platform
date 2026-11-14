package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.CulturaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.CulturaResponse;
import br.com.agricoladigital.smart_farming_api.entity.Cultura;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.CulturaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CulturaServiceTest {
    @Mock private CulturaRepository culturaRepository;
    @Mock private PlantioRepository plantioRepository;
    @InjectMocks private CulturaService culturaService;

    @Test
    void criarGravaNomeSemEspacosNasPontas() {
        when(culturaRepository.existsByNomeIgnoreCase("Soja")).thenReturn(false);
        when(culturaRepository.save(any(Cultura.class))).thenAnswer(i -> i.getArgument(0));

        CulturaResponse response = culturaService.criar(new CulturaRequest("  Soja ", 120));

        assertEquals("Soja", response.nome());
        assertEquals(120, response.cicloDias());
    }

    @Test
    void criarRejeitaNomeDuplicadoIgnorandoCaixa() {
        when(culturaRepository.existsByNomeIgnoreCase("soja")).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> culturaService.criar(new CulturaRequest("soja", null)));
        verify(culturaRepository, never()).save(any());
    }

    @Test
    void atualizarPermiteManterOProprioNome() {
        Cultura cultura = new Cultura("Soja", 100);
        when(culturaRepository.findById(1L)).thenReturn(Optional.of(cultura));
        when(culturaRepository.existsByNomeIgnoreCaseAndIdNot("Soja", 1L)).thenReturn(false);

        CulturaResponse response = culturaService.atualizar(1L, new CulturaRequest("Soja", 110));

        assertEquals(110, response.cicloDias());
    }

    @Test
    void atualizarRejeitaNomeDeOutraCultura() {
        when(culturaRepository.findById(1L)).thenReturn(Optional.of(new Cultura("Soja", null)));
        when(culturaRepository.existsByNomeIgnoreCaseAndIdNot("Milho", 1L)).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> culturaService.atualizar(1L, new CulturaRequest("Milho", null)));
    }

    @Test
    void excluirRejeitaCulturaComPlantios() {
        when(culturaRepository.findById(1L)).thenReturn(Optional.of(new Cultura("Soja", null)));
        when(plantioRepository.existsByCultura_Id(1L)).thenReturn(true);

        assertThrows(ConflitoException.class, () -> culturaService.excluir(1L));
        verify(culturaRepository, never()).delete(any(Cultura.class));
    }

    @Test
    void buscarRejeitaCulturaInexistente() {
        when(culturaRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> culturaService.buscar(9L));
    }
}
