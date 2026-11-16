package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.service.ConciliadorAlertas.Acao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Avalia periodicamente as regras de alerta de todas as fazendas. A falha de uma fazenda é registrada
 * e não impede as demais. Para desligar: {@code app.alertas.avaliacao-automatica=false}.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.alertas.avaliacao-automatica", havingValue = "true", matchIfMissing = true)
public class AtualizadorAlertas {
    private final FazendaRepository fazendaRepository;
    private final AvaliadorAlertas avaliador;

    public AtualizadorAlertas(FazendaRepository fazendaRepository, AvaliadorAlertas avaliador) {
        this.fazendaRepository = fazendaRepository;
        this.avaliador = avaliador;
    }

    @Scheduled(
            fixedRateString = "#{${app.alertas.avaliacao-minutos:15} * 60}",
            initialDelayString = "${app.alertas.atraso-inicial-segundos:60}",
            timeUnit = TimeUnit.SECONDS
    )
    public void avaliarTodas() {
        List<Long> fazendas = fazendaRepository.listarIds();
        int avaliadas = 0;
        int criados = 0;
        int escalados = 0;
        int resolvidos = 0;
        for (Long id : fazendas) {
            try {
                Map<Acao, Integer> acoes = avaliador.avaliarFazenda(id);
                criados += acoes.getOrDefault(Acao.CRIADO, 0);
                escalados += acoes.getOrDefault(Acao.ESCALADO, 0);
                resolvidos += acoes.getOrDefault(Acao.RESOLVIDO_AUTOMATICAMENTE, 0);
                avaliadas++;
            } catch (RuntimeException erro) {
                log.warn("Alertas da fazenda {} não avaliados: {}", id, erro.getMessage());
            }
        }
        log.info("Alertas avaliados em {} de {} fazendas: {} criados, {} escalados, {} resolvidos automaticamente.",
                avaliadas, fazendas.size(), criados, escalados, resolvidos);
    }
}
