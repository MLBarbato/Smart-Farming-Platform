package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.TalhaoRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.TalhaoResponse;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Talhao;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import br.com.agricoladigital.smart_farming_api.repository.DispositivoRepository;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import br.com.agricoladigital.smart_farming_api.repository.RecomendacaoIrrigacaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TalhaoServiceTest {
    @Mock
    private TalhaoRepository talhaoRepository;

    @Mock
    private FazendaRepository fazendaRepository;

    @Mock
    private DispositivoRepository dispositivoRepository;

    @Mock
    private PlantioRepository plantioRepository;

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private RecomendacaoIrrigacaoRepository recomendacaoIrrigacaoRepository;

    @InjectMocks
    private TalhaoService talhaoService;

    @Test
    void criarAssociaTalhaoAFazendaInformada() {
        long fazendaId = 4L;
        Fazenda fazenda = new Fazenda(
                "Fazenda Horizonte",
                "Goiás",
                1200.5,
                null, null, null, null,
                new Usuario("Ana Silva", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR)
        );
        TalhaoRequest request = new TalhaoRequest("T-01", 50.0, null);
        when(fazendaRepository.findById(fazendaId)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.save(any(Talhao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TalhaoResponse response = talhaoService.criar(fazendaId, request);

        ArgumentCaptor<Talhao> talhaoCaptor = ArgumentCaptor.forClass(Talhao.class);
        verify(talhaoRepository).save(talhaoCaptor.capture());
        assertSame(fazenda, talhaoCaptor.getValue().getFazenda());
        assertEquals(request.identificacao(), response.identificacao());
        assertEquals(request.areaHectares(), response.areaHectares());
    }

    @Test
    void buscarNaoRetornaTalhaoDeOutraFazenda() {
        when(fazendaRepository.findById(4L)).thenReturn(Optional.of(
                new Fazenda(
                        "Fazenda",
                        "Goiás",
                        100.0,
                        null, null, null, null,
                        new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR)
                )
        ));
        when(talhaoRepository.findByIdAndFazenda_Id(10L, 4L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> talhaoService.buscar(4L, 10L));
    }

    @Test
    void excluirRejeitaTalhaoComDispositivosVinculados() {
        Fazenda fazenda = new Fazenda(
                "Fazenda",
                "Goiás",
                100.0,
                null, null, null, null,
                new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR)
        );
        Talhao talhao = new Talhao("T-01", 50.0, null, fazenda);
        when(fazendaRepository.findById(4L)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.findByIdAndFazenda_Id(10L, 4L)).thenReturn(Optional.of(talhao));
        when(dispositivoRepository.existsByTalhao_Id(10L)).thenReturn(true);

        assertThrows(ConflitoException.class, () -> talhaoService.excluir(4L, 10L));

        verify(talhaoRepository, never()).delete(any(Talhao.class));
    }
}
