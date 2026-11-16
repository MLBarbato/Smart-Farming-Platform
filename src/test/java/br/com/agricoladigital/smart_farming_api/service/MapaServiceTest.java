package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse.TalhaoMapa;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoAlertasResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.ContagemAlerta;
import br.com.agricoladigital.smart_farming_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MapaServiceTest {
    private static final String POLIGONO =
            "{\"type\":\"Polygon\",\"coordinates\":[[[-50.93,-17.8],[-50.91,-17.8],[-50.91,-17.78],[-50.93,-17.8]]]}";

    @Mock private FazendaRepository fazendaRepository;
    @Mock private TalhaoRepository talhaoRepository;
    @Mock private PlantioRepository plantioRepository;
    @Mock private SensorRepository sensorRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private AlertaRepository alertaRepository;
    @InjectMocks private MapaService mapaService;

    private Fazenda fazenda;
    private Talhao talhaoA;
    private Talhao talhaoB;

    @BeforeEach
    void preparar() {
        Usuario dono = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("Santa Maria", "Rio Verde", 1240.0, -17.79, -50.92, "Rio Verde", "GO", dono);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        talhaoA = new Talhao("A1", 50.0, POLIGONO, fazenda);
        ReflectionTestUtils.setField(talhaoA, "id", 10L);
        talhaoB = new Talhao("B1", 30.0, null, fazenda);
        ReflectionTestUtils.setField(talhaoB, "id", 11L);
    }

    private Sensor sensor(long id, Talhao talhao, VariavelMonitorada variavel, StatusDispositivo status) {
        Sensor sensor = new Sensor(new Dispositivo("S-" + id, "SENSOR", null, status, talhao), variavel);
        ReflectionTestUtils.setField(sensor, "id", id);
        ReflectionTestUtils.setField(sensor.getDispositivo(), "id", id);
        return sensor;
    }

    @Test
    void montaFazendaTalhoesPlantioSensoresELeituras() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.findAllByFazenda_Id(1L)).thenReturn(List.of(talhaoB, talhaoA));
        Plantio plantio = new Plantio(talhaoA, new Cultura("Soja", 120), "Direto", "2026/2027",
                LocalDate.of(2026, 10, 5), LocalDate.of(2027, 2, 10));
        ReflectionTestUtils.setField(plantio, "id", 7L);
        ReflectionTestUtils.setField(plantio, "status", StatusPlantio.EM_CULTIVO);
        when(plantioRepository.buscarPorFazendaEStatus(1L, StatusPlantio.EM_CULTIVO)).thenReturn(List.of(plantio));
        Sensor temperatura = sensor(100, talhaoA, VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        Sensor solo = sensor(101, talhaoA, VariavelMonitorada.UMIDADE_SOLO, StatusDispositivo.INATIVO);
        when(sensorRepository.listarPorFazenda(1L)).thenReturn(List.of(solo, temperatura));
        Leitura leitura = new Leitura(temperatura.getDispositivo(), 24.5, LocalDateTime.of(2026, 11, 16, 12, 0),
                OrigemLeitura.SIMULADA);
        when(leituraRepository.ultimasPorDispositivos(any())).thenReturn(List.of(leitura));

        MapaFazendaResponse mapa = mapaService.mapaDaFazenda(1L);

        assertEquals("Santa Maria", mapa.fazenda().nome());
        assertEquals(-17.79, mapa.fazenda().latitude());
        assertEquals("GO", mapa.fazenda().estado());
        assertEquals(List.of("A1", "B1"), mapa.talhoes().stream().map(TalhaoMapa::identificacao).toList());

        TalhaoMapa a = mapa.talhoes().get(0);
        assertEquals("Polygon", a.geometria().get("type").asString());
        assertEquals("Soja", a.plantioAtual().culturaNome());
        assertEquals(StatusPlantio.EM_CULTIVO, a.plantioAtual().status());
        assertEquals(2, a.sensores().total());
        assertEquals(1, a.sensores().ativos());
        assertEquals(1, a.leiturasAtuais().size());
        assertEquals(24.5, a.leiturasAtuais().get(0).valor());
        assertEquals("°C", a.leiturasAtuais().get(0).unidade());
    }

    @Test
    void talhaoSemGeometriaPlantioSensoresOuLeiturasVemVazio() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.findAllByFazenda_Id(1L)).thenReturn(List.of(talhaoB));
        when(plantioRepository.buscarPorFazendaEStatus(1L, StatusPlantio.EM_CULTIVO)).thenReturn(List.of());
        when(sensorRepository.listarPorFazenda(1L)).thenReturn(List.of());

        TalhaoMapa b = mapaService.mapaDaFazenda(1L).talhoes().get(0);

        assertNull(b.geometria());
        assertNull(b.plantioAtual());
        assertEquals(0, b.sensores().total());
        assertTrue(b.leiturasAtuais().isEmpty());
        verify(leituraRepository, never()).ultimasPorDispositivos(any());
    }

    @Test
    void sensorSemLeituraNaoApareceEmLeiturasAtuais() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.findAllByFazenda_Id(1L)).thenReturn(List.of(talhaoA));
        when(plantioRepository.buscarPorFazendaEStatus(1L, StatusPlantio.EM_CULTIVO)).thenReturn(List.of());
        when(sensorRepository.listarPorFazenda(1L))
                .thenReturn(List.of(sensor(100, talhaoA, VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO)));
        when(leituraRepository.ultimasPorDispositivos(any())).thenReturn(List.of());

        TalhaoMapa a = mapaService.mapaDaFazenda(1L).talhoes().get(0);

        assertEquals(1, a.sensores().ativos());
        assertTrue(a.leiturasAtuais().isEmpty());
    }

    @Test
    void fazendaInexistenteRetornaNaoEncontrado() {
        when(fazendaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> mapaService.mapaDaFazenda(99L));
    }

    @Test
    void cadaTalhaoTrazSeusAlertasAbertosEAMaiorCriticidade() {
        when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
        when(talhaoRepository.findAllByFazenda_Id(1L)).thenReturn(List.of(talhaoA, talhaoB));
        when(alertaRepository.contarPorTalhaoECriticidade(any(), any())).thenReturn(List.of(
                new ContagemAlerta(1L, 10L, CriticidadeAlerta.BAIXA, 1),
                new ContagemAlerta(1L, 10L, CriticidadeAlerta.MEDIA, 1)));

        MapaFazendaResponse mapa = mapaService.mapaDaFazenda(1L);

        ResumoAlertasResponse a = mapa.talhoes().stream().filter(t -> t.id() == 10L).findFirst().orElseThrow().alertas();
        assertEquals(2, a.abertos());
        assertEquals(1, a.baixa());
        assertEquals(1, a.media());
        assertEquals(CriticidadeAlerta.MEDIA, a.maiorCriticidade());
        ResumoAlertasResponse b = mapa.talhoes().stream().filter(t -> t.id() == 11L).findFirst().orElseThrow().alertas();
        assertEquals(0, b.abertos());
        assertNull(b.maiorCriticidade());
        assertEquals(2, mapa.alertas().abertos());
    }
}
