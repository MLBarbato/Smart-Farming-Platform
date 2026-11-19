package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {
    /** 02:00 UTC de 19/11 = 23:00 de 18/11 em Brasília: "hoje" é 18/11. */
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 19, 2, 0);
    private static final LocalDate DE = LocalDate.of(2026, 11, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 11, 7);
    private static final LocalDateTime DE_UTC = LocalDateTime.of(2026, 11, 1, 3, 0);
    private static final LocalDateTime ATE_UTC = LocalDateTime.of(2026, 11, 8, 3, 0);

    @Mock private FazendaRepository fazendaRepository;
    @Mock private RecomendacaoIrrigacaoRepository recomendacaoRepository;
    @Mock private AlertaRepository alertaRepository;
    @Mock private PlantioRepository plantioRepository;
    @Mock private SensorRepository sensorRepository;
    @Mock private LeituraRepository leituraRepository;
    @Mock private EstacaoMeteorologicaRepository estacaoRepository;

    private RelatorioService service;
    private Fazenda fazenda;
    private Talhao talhao;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        service = new RelatorioService(fazendaRepository, recomendacaoRepository, alertaRepository, plantioRepository,
                sensorRepository, leituraRepository, estacaoRepository, 30,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        ana = new Usuario("Ana", "ana@example.com", "hash", PerfilUsuario.AGRICULTOR);
        fazenda = new Fazenda("F", null, 10.0, -17.0, -50.0, null, null, ana);
        ReflectionTestUtils.setField(fazenda, "id", 1L);
        talhao = new Talhao("Talhão Norte", 4.0, null, fazenda);
        ReflectionTestUtils.setField(talhao, "id", 10L);
        lenient().when(fazendaRepository.findById(1L)).thenReturn(Optional.of(fazenda));
    }

    private static String texto(RelatorioService.Arquivo arquivo) {
        return new String(arquivo.conteudo(), StandardCharsets.UTF_8).substring(1);   // sem o BOM
    }

    private static String[] linhas(RelatorioService.Arquivo arquivo) {
        return texto(arquivo).split("\r\n");
    }

    private Sensor sensor(Long id, String codigo, VariavelMonitorada variavel, StatusDispositivo status) {
        Dispositivo d = new Dispositivo(codigo, "SENSOR", LocalDate.of(2026, 10, 1), status, talhao);
        ReflectionTestUtils.setField(d, "id", id);
        Sensor s = new Sensor(d, variavel);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    // ---- período ----

    @Test
    void semDatasUsaOsUltimos30DiasAteHojeNoHorarioDeBrasilia() {
        RelatorioService.Arquivo arquivo = service.gerar(1L, TipoRelatorio.ALERTAS, null, null);

        verify(alertaRepository).buscarNoPeriodo(1L, LocalDateTime.of(2026, 10, 20, 3, 0), LocalDateTime.of(2026, 11, 19, 3, 0));
        assertEquals("relatorio-alertas-fazenda-1-2026-10-20-a-2026-11-18.csv", arquivo.nome());
    }

    @Test
    void periodoInvertidoOuLongoDemaisEhRejeitado() {
        assertThrows(RegraDeNegocioException.class, () -> service.gerar(1L, TipoRelatorio.ALERTAS, ATE, DE));
        assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(1L, TipoRelatorio.ALERTAS, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)),
                "367 dias passam do limite");
        assertDoesNotThrow(() -> service.gerar(1L, TipoRelatorio.ALERTAS, LocalDate.of(2025, 1, 2), LocalDate.of(2026, 1, 2)),
                "366 dias é o limite");
    }

    @Test
    void fazendaInexistenteNaoGeraRelatorio() {
        when(fazendaRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.gerar(9L, TipoRelatorio.IRRIGACAO, DE, ATE));
    }

    // ---- tipos ----

    @Test
    void irrigacaoTrazOsInsumosComRotulosEmPortugues() {
        InsumosIrrigacao insumos = new InsumosIrrigacao("Soja", 30.0, 38.0, 30, 28.0, OrigemUmidade.SENSOR_TALHAO,
                30.0, 5.0, 4.0, 31.0, false, List.of());
        RecomendacaoIrrigacao r = new RecomendacaoIrrigacao(talhao, null, LocalDateTime.of(2026, 11, 2, 15, 0),
                LocalDateTime.of(2026, 11, 2, 21, 0), SituacaoIrrigacao.IRRIGAR, 26.0, "Volume sugerido: 26 mm.", insumos);
        when(recomendacaoRepository.buscarNoPeriodo(1L, DE_UTC, ATE_UTC)).thenReturn(List.of(r));

        String[] linhas = linhas(service.gerar(1L, TipoRelatorio.IRRIGACAO, DE, ATE));

        assertEquals(2, linhas.length);
        assertTrue(linhas[0].startsWith("Data/hora do cálculo;Talhão;Cultura;Situação;Volume recomendado (mm)"));
        assertEquals("02/11/2026 12:00;Talhão Norte;Soja;Irrigar;26;28;Sensor do talhão;30;38;30;30;5;4;31;"
                + "02/11/2026 18:00;;Volume sugerido: 26 mm.", linhas[1]);
    }

    @Test
    void climaJuntaAsVariaveisDaEstacaoPorHoraComAtribuicao() {
        Dispositivo dEstacao = new Dispositivo("EST-1", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda);
        EstacaoMeteorologica estacao = new EstacaoMeteorologica(dEstacao, "m", 60, FonteEstacao.OPEN_METEO);
        ReflectionTestUtils.setField(estacao, "id", 50L);
        Dispositivo dTemp = new Dispositivo("EST-1-T", "SENSOR", null, StatusDispositivo.ATIVO, fazenda);
        ReflectionTestUtils.setField(dTemp, "id", 51L);
        Sensor temp = new Sensor(dTemp, VariavelMonitorada.TEMPERATURA_AR, estacao);
        ReflectionTestUtils.setField(temp, "id", 51L);
        Dispositivo dChuva = new Dispositivo("EST-1-P", "SENSOR", null, StatusDispositivo.ATIVO, fazenda);
        ReflectionTestUtils.setField(dChuva, "id", 52L);
        Sensor chuva = new Sensor(dChuva, VariavelMonitorada.PRECIPITACAO, estacao);
        ReflectionTestUtils.setField(chuva, "id", 52L);
        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.of(estacao));
        when(sensorRepository.listarDaEstacao(50L)).thenReturn(List.of(temp, chuva));
        LocalDateTime h1 = LocalDateTime.of(2026, 11, 2, 17, 0);
        LocalDateTime h2 = LocalDateTime.of(2026, 11, 2, 18, 0);
        when(leituraRepository.buscarNoPeriodo(anyCollection(), eq(DE_UTC), eq(ATE_UTC))).thenReturn(List.of(
                new Leitura(dTemp, 28.6, h1, OrigemLeitura.EXTERNA),
                new Leitura(dChuva, 0.0, h1, OrigemLeitura.EXTERNA),
                new Leitura(dTemp, 21.5, h2, OrigemLeitura.EXTERNA)));

        String[] linhas = linhas(service.gerar(1L, TipoRelatorio.CLIMA, DE, ATE));

        assertEquals("Data/hora;Temperatura do ar (°C);Umidade do ar (%);Velocidade do vento (km/h);"
                + "Precipitação (mm);Umidade do solo (%);Fonte", linhas[0]);
        assertEquals(3, linhas.length);
        assertTrue(linhas[1].startsWith("02/11/2026 14:00;28,6;;;0;;Dados meteorológicos por Open-Meteo.com"));
        assertTrue(linhas[2].startsWith("02/11/2026 15:00;21,5;;;;;"), "hora sem chuva fica vazia, não zero");
    }

    @Test
    void climaSemEstacaoInformaComoResolver() {
        when(estacaoRepository.buscarDaFazenda(1L)).thenReturn(Optional.empty());

        RecursoNaoEncontradoException erro = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.gerar(1L, TipoRelatorio.CLIMA, DE, ATE));
        assertTrue(erro.getMessage().contains("estação virtual"));
    }

    @Test
    void sensoresResumemOPeriodoEInformamAComunicacaoAtual() {
        Sensor emDia = sensor(1L, "S-1", VariavelMonitorada.UMIDADE_SOLO, StatusDispositivo.ATIVO);
        Sensor atrasado = sensor(2L, "S-2", VariavelMonitorada.TEMPERATURA_AR, StatusDispositivo.ATIVO);
        Sensor semLeitura = sensor(3L, "S-3", VariavelMonitorada.PH_SOLO, StatusDispositivo.ATIVO);
        Sensor parado = sensor(4L, "S-4", VariavelMonitorada.UMIDADE_AR, StatusDispositivo.MANUTENCAO);
        when(sensorRepository.listarPorFazenda(1L)).thenReturn(List.of(parado, semLeitura, atrasado, emDia));
        when(leituraRepository.resumirNoPeriodo(anyCollection(), eq(DE_UTC), eq(ATE_UTC))).thenReturn(List.of(
                new ResumoLeiturasSensor(1L, 3, LocalDateTime.of(2026, 11, 1, 3, 0), LocalDateTime.of(2026, 11, 7, 3, 0),
                        40.0, 44.333, 47.64)));
        when(leituraRepository.ultimasPorDispositivos(anyCollection())).thenReturn(List.of(
                new Leitura(emDia.getDispositivo(), 44.0, AGORA.minusMinutes(5), OrigemLeitura.SIMULADA),
                new Leitura(atrasado.getDispositivo(), 25.0, AGORA.minusHours(2), OrigemLeitura.SIMULADA)));

        String[] linhas = linhas(service.gerar(1L, TipoRelatorio.SENSORES, DE, ATE));

        assertEquals(5, linhas.length);
        assertEquals("S-1;Talhão Norte;Umidade do solo;%;Ativo;01/10/2026;3;01/11/2026 00:00;07/11/2026 00:00;"
                + "40;44,33;47,64;18/11/2026 22:55;Em dia", linhas[1], "ordenados por talhão e código");
        assertTrue(linhas[2].startsWith("S-2;") && linhas[2].endsWith(";Sem leitura recente"));
        assertTrue(linhas[3].startsWith("S-3;") && linhas[3].contains(";0;") && linhas[3].endsWith(";Sem leituras"));
        assertTrue(linhas[4].startsWith("S-4;") && linhas[4].contains("Em manutenção") && linhas[4].endsWith(";Não monitorado"));
    }

    @Test
    void sensoresSemNenhumSensorTrazemSoOCabecalho() {
        when(sensorRepository.listarPorFazenda(1L)).thenReturn(List.of());

        assertEquals(1, linhas(service.gerar(1L, TipoRelatorio.SENSORES, DE, ATE)).length);
        verifyNoInteractions(leituraRepository);
    }

    @Test
    void alertasTrazemOAtendimentoEOTempoAteOFechamento() {
        RegraAlerta regra = new RegraAlerta(EventoAlerta.SECA, CriticidadeAlerta.ALTA, OperadorRegra.MENOR_QUE,
                VariavelMonitorada.UMIDADE_SOLO, 20.0, "%", 3, "seca");
        Alerta resolvido = new Alerta(regra, talhao, null, LocalDateTime.of(2026, 11, 2, 12, 0), "Seca: 18 %");
        resolvido.resolverManualmente(ana, LocalDateTime.of(2026, 11, 2, 13, 30), "irrigado");
        Alerta aberto = new Alerta(regra, talhao, null, LocalDateTime.of(2026, 11, 3, 12, 0), "Seca: 19 %");
        when(alertaRepository.buscarNoPeriodo(1L, DE_UTC, ATE_UTC)).thenReturn(List.of(resolvido, aberto));

        String[] linhas = linhas(service.gerar(1L, TipoRelatorio.ALERTAS, DE, ATE));

        assertEquals("02/11/2026 09:00;Talhão Norte;Seca;Alta;Resolvido;Seca: 18 %;Ana;02/11/2026 10:30;"
                + "02/11/2026 10:30;Manual;irrigado;1,5", linhas[1]);
        assertEquals("03/11/2026 09:00;Talhão Norte;Seca;Alta;Aberto;Seca: 19 %;;;;;;", linhas[2]);
    }

    @Test
    void produtividadeCalculaKgPorHectareSoComColheita() {
        Cultura soja = new Cultura("Soja", 120, 30.0, 38.0, 30);
        Plantio colhido = new Plantio(talhao, soja, "Direto", "2025/2026", LocalDate.of(2025, 10, 1), LocalDate.of(2026, 2, 1));
        colhido.colher(LocalDate.of(2026, 2, 10), 14_000.0);
        Plantio emCultivo = new Plantio(talhao, soja, "Direto", "2026/2027", LocalDate.of(2026, 10, 1), null);
        emCultivo.alterarStatus(StatusPlantio.EM_CULTIVO);
        when(plantioRepository.buscarNoPeriodo(1L, DE, ATE)).thenReturn(List.of(colhido, emCultivo));

        String[] linhas = linhas(service.gerar(1L, TipoRelatorio.PRODUTIVIDADE, DE, ATE));

        assertEquals("Talhão Norte;4;Soja;2025/2026;Direto;Colhido;01/10/2025;01/02/2026;10/02/2026;14000;3500", linhas[1]);
        assertEquals("Talhão Norte;4;Soja;2026/2027;Direto;Em cultivo;01/10/2026;;;;", linhas[2]);
    }

    @Test
    void todoTipoTemRotuloEArquivo() {
        when(sensorRepository.listarPorFazenda(any())).thenReturn(List.of());
        for (TipoRelatorio tipo : TipoRelatorio.values()) {
            if (tipo == TipoRelatorio.CLIMA) {
                continue;
            }
            RelatorioService.Arquivo arquivo = service.gerar(1L, tipo, DE, ATE);
            assertTrue(arquivo.nome().startsWith("relatorio-" + tipo.getArquivo() + "-"), tipo.name());
            assertEquals('﻿', new String(arquivo.conteudo(), StandardCharsets.UTF_8).charAt(0));
        }
    }
}
