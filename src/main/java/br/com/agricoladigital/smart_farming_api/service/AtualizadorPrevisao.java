package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Atualiza periodicamente a previsão de todas as fazendas com coordenadas. A falha de uma fazenda
 * (ou do provedor) é registrada e não impede as demais; a previsão anterior é mantida. Para
 * desligar: {@code app.previsao.atualizacao-automatica=false}.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.previsao.atualizacao-automatica", havingValue = "true", matchIfMissing = true)
public class AtualizadorPrevisao {
    private final FazendaRepository fazendaRepository;
    private final PrevisaoService previsaoService;

    public AtualizadorPrevisao(FazendaRepository fazendaRepository, PrevisaoService previsaoService) {
        this.fazendaRepository = fazendaRepository;
        this.previsaoService = previsaoService;
    }

    @Scheduled(
            fixedRateString = "#{${app.previsao.atualizacao-minutos:180} * 60}",
            initialDelayString = "${app.previsao.atraso-inicial-segundos:30}",
            timeUnit = TimeUnit.SECONDS
    )
    public void atualizarTodas() {
        List<Fazenda> fazendas = fazendaRepository.findAllByLatitudeIsNotNullAndLongitudeIsNotNull();
        int atualizadas = 0;
        for (Fazenda fazenda : fazendas) {
            try {
                previsaoService.atualizar(fazenda);
                atualizadas++;
            } catch (RuntimeException erro) {
                log.warn("Previsão da fazenda {} não atualizada: {}", fazenda.getId(), erro.getMessage());
            }
        }
        log.info("Previsão atualizada para {} de {} fazendas.", atualizadas, fazendas.size());
    }
}
