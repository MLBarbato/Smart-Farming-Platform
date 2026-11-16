package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.CenarioSimulacao;
import br.com.agricoladigital.smart_farming_api.entity.EstacaoMeteorologica;
import br.com.agricoladigital.smart_farming_api.entity.OrigemLeitura;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Simulador mínimo de sensores: a cada intervalo gera uma leitura {@code SIMULADA} para cada
 * sensor ATIVO, usando o mesmo serviço de ingestão das leituras reais. Fica desligado por padrão;
 * ligue com {@code app.simulador.enabled=true} (variável {@code SIMULADOR_ENABLED}).
 *
 * <p>Quando um sensor ativo ainda não tem nenhuma leitura, o simulador primeiro preenche o
 * histórico retroativo ({@code app.simulador.historico-horas}), para que gráficos e consultas
 * históricas tenham dados desde o início. Sensores que já têm leituras nunca são preenchidos.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.simulador.enabled", havingValue = "true")
public class SimuladorSensores {
    static final int MAXIMO_HORAS_DE_HISTORICO = 24 * 90;

    private final SensorRepository sensorRepository;
    private final LeituraService leituraService;
    private final EstacaoMeteorologicaRepository estacaoRepository;
    private final EstadoSimulacao estado;
    private final GeradorLeituraSimulada gerador = new GeradorLeituraSimulada(ThreadLocalRandom.current());
    private final Clock clock = Clock.systemUTC();
    private final Duration janelaHistorico;
    private final Duration passoHistorico;

    public SimuladorSensores(
            SensorRepository sensorRepository,
            LeituraService leituraService,
            EstacaoMeteorologicaRepository estacaoRepository,
            EstadoSimulacao estado,
            @Value("${app.simulador.historico-horas:72}") long historicoHoras,
            @Value("${app.simulador.historico-intervalo-minutos:15}") long historicoIntervaloMinutos
    ) {
        if (historicoHoras < 0 || historicoHoras > MAXIMO_HORAS_DE_HISTORICO || historicoIntervaloMinutos < 1) {
            throw new IllegalArgumentException(
                    "app.simulador.historico-horas deve estar entre 0 e " + MAXIMO_HORAS_DE_HISTORICO
                            + " e historico-intervalo-minutos deve ser positivo."
            );
        }
        this.sensorRepository = sensorRepository;
        this.leituraService = leituraService;
        this.estacaoRepository = estacaoRepository;
        this.estado = estado;
        this.janelaHistorico = Duration.ofHours(historicoHoras);
        this.passoHistorico = Duration.ofMinutes(historicoIntervaloMinutos);
    }

    /** Já gera o histórico dos sensores existentes na subida, sem esperar o primeiro intervalo. */
    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        gerarLeituras();
    }

    @Scheduled(
            fixedRateString = "${app.simulador.intervalo-segundos:60}",
            initialDelayString = "${app.simulador.intervalo-segundos:60}",
            timeUnit = TimeUnit.SECONDS
    )
    public void gerarLeituras() {
        Instant agora = clock.instant();
        LocalDateTime dataHora = LocalDateTime.ofInstant(agora, ZoneOffset.UTC).withNano(0);
        List<Sensor> sensores = sensorRepository.listarPorStatus(StatusDispositivo.ATIVO);
        int gravadas = 0;
        for (Sensor sensor : sensores) {
            CenarioSimulacao cenario = estado.de(sensor.getDispositivo().getFazenda().getId());
            if (cenario == CenarioSimulacao.FALHA_SENSOR) {
                continue; // cenário de falha: o sensor "para de enviar"
            }
            try {
                preencherHistoricoSeVazio(sensor, agora);
                double valor = cenario == CenarioSimulacao.SECA && sensor.getTipoSensor() == VariavelMonitorada.UMIDADE_SOLO
                        ? GeradorLeituraSimulada.UMIDADE_SOLO_EM_SECA
                        : gerador.gerar(sensor.getTipoSensor(), agora);
                leituraService.registrar(sensor, valor, dataHora, OrigemLeitura.SIMULADA);
                gravadas++;
            } catch (RuntimeException erro) {
                // Um sensor com problema não pode impedir os demais.
                log.warn("Simulador: leitura do sensor {} não gravada: {}", sensor.getId(), erro.getMessage());
            }
        }
        log.debug("Simulador: {} leituras gravadas de {} sensores ativos.", gravadas, sensores.size());
        gerarGeadaNasEstacoes(dataHora);
    }

    /** No cenário GEADA, o sensor de temperatura da estação da fazenda marca perto de zero. */
    private void gerarGeadaNasEstacoes(LocalDateTime dataHora) {
        for (EstacaoMeteorologica estacao : estacaoRepository.listarPorStatus(StatusDispositivo.ATIVO)) {
            if (estado.de(estacao.getDispositivo().getFazenda().getId()) != CenarioSimulacao.GEADA) {
                continue;
            }
            for (Sensor sensor : sensorRepository.listarDaEstacao(estacao.getId())) {
                if (sensor.getTipoSensor() != VariavelMonitorada.TEMPERATURA_AR
                        || sensor.getDispositivo().getStatus() != StatusDispositivo.ATIVO) {
                    continue;
                }
                try {
                    leituraService.registrar(sensor, GeradorLeituraSimulada.TEMPERATURA_EM_GEADA, dataHora,
                            OrigemLeitura.SIMULADA);
                } catch (ConflitoException jaExiste) {
                    // já há leitura neste segundo; a próxima rodada grava de novo
                } catch (RuntimeException erro) {
                    log.warn("Simulador: geada na estação {} não gravada: {}", estacao.getId(), erro.getMessage());
                }
            }
        }
    }

    private void preencherHistoricoSeVazio(Sensor sensor, Instant agora) {
        if (janelaHistorico.isZero() || leituraService.temLeituras(sensor.getId())) {
            return;
        }
        List<LeituraService.Medicao> medicoes = gerador
                .gerarHistorico(sensor.getTipoSensor(), agora, janelaHistorico, passoHistorico)
                .stream()
                .map(ponto -> new LeituraService.Medicao(
                        LocalDateTime.ofInstant(ponto.instante(), ZoneOffset.UTC), ponto.valor()))
                .toList();
        int gravadas = leituraService.registrarLote(sensor, medicoes, OrigemLeitura.SIMULADA);
        log.info("Simulador: {} leituras de histórico geradas para o sensor {} ({}).",
                gravadas, sensor.getId(), sensor.getTipoSensor());
    }
}
