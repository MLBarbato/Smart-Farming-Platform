package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.EstacaoMeteorologica;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sincroniza periodicamente todas as estações ativas. A falha de uma (ou do provedor) é registrada
 * e não impede as demais; as próximas execuções recuperam as horas perdidas. Para desligar:
 * {@code app.estacao.atualizacao-automatica=false}.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.estacao.atualizacao-automatica", havingValue = "true", matchIfMissing = true)
public class AtualizadorEstacao {
    private final EstacaoMeteorologicaRepository estacaoRepository;
    private final EstacaoSincronizador sincronizador;

    public AtualizadorEstacao(EstacaoMeteorologicaRepository estacaoRepository, EstacaoSincronizador sincronizador) {
        this.estacaoRepository = estacaoRepository;
        this.sincronizador = sincronizador;
    }

    @Scheduled(
            fixedRateString = "#{${app.estacao.atualizacao-minutos:60} * 60}",
            initialDelayString = "${app.estacao.atraso-inicial-segundos:45}",
            timeUnit = TimeUnit.SECONDS
    )
    public void sincronizarTodas() {
        List<EstacaoMeteorologica> estacoes = estacaoRepository.listarPorStatus(StatusDispositivo.ATIVO);
        int leituras = 0;
        int sincronizadas = 0;
        for (EstacaoMeteorologica estacao : estacoes) {
            try {
                leituras += sincronizador.sincronizar(estacao);
                sincronizadas++;
            } catch (RuntimeException erro) {
                log.warn("Estação {} não sincronizada: {}", estacao.getId(), erro.getMessage());
            }
        }
        log.info("Estações sincronizadas: {} de {} ({} leituras novas).", sincronizadas, estacoes.size(), leituras);
    }
}
