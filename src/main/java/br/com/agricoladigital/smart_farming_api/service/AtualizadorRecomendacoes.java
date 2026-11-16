package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Calcula periodicamente as recomendações de irrigação de todas as fazendas. A falha de uma fazenda é
 * registrada e não impede as demais. Para desligar: {@code app.irrigacao.calculo-automatico=false}.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.irrigacao.calculo-automatico", havingValue = "true", matchIfMissing = true)
public class AtualizadorRecomendacoes {
    private final FazendaRepository fazendaRepository;
    private final RecomendadorIrrigacao recomendador;

    public AtualizadorRecomendacoes(FazendaRepository fazendaRepository, RecomendadorIrrigacao recomendador) {
        this.fazendaRepository = fazendaRepository;
        this.recomendador = recomendador;
    }

    @Scheduled(
            fixedRateString = "#{${app.irrigacao.calculo-minutos:180} * 60}",
            initialDelayString = "${app.irrigacao.atraso-inicial-segundos:90}",
            timeUnit = TimeUnit.SECONDS
    )
    public void calcularTodas() {
        List<Long> fazendas = fazendaRepository.listarIds();
        int calculadas = 0;
        int gravadas = 0;
        int processadas = 0;
        for (Long id : fazendas) {
            try {
                RecomendadorIrrigacao.ResumoCalculo resumo = recomendador.calcularFazenda(id);
                calculadas += resumo.calculadas();
                gravadas += resumo.gravadas();
                processadas++;
            } catch (RuntimeException erro) {
                log.warn("Recomendações da fazenda {} não calculadas: {}", id, erro.getMessage());
            }
        }
        log.info("Irrigação: {} de {} fazendas processadas, {} recomendações calculadas, {} gravadas.",
                processadas, fazendas.size(), calculadas, gravadas);
    }
}
