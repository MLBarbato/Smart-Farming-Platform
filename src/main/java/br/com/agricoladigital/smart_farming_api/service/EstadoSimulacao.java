package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.CenarioSimulacao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cenário vigente do simulador: um padrão para todas as fazendas e exceções por fazenda (em memória). */
@Component
@ConditionalOnProperty(name = "app.simulador.enabled", havingValue = "true")
public class EstadoSimulacao {
    private volatile CenarioSimulacao padrao = CenarioSimulacao.NORMAL;
    private final Map<Long, CenarioSimulacao> porFazenda = new ConcurrentHashMap<>();

    public CenarioSimulacao de(Long fazendaId) {
        return porFazenda.getOrDefault(fazendaId, padrao);
    }

    /** Sem {@code fazendaId}, vale para todas as fazendas e descarta as exceções. */
    public void definir(Long fazendaId, CenarioSimulacao cenario) {
        if (fazendaId == null) {
            porFazenda.clear();
            padrao = cenario;
        } else {
            porFazenda.put(fazendaId, cenario);
        }
    }

    public CenarioSimulacao padrao() {
        return padrao;
    }

    public Map<Long, CenarioSimulacao> excecoes() {
        return Map.copyOf(porFazenda);
    }
}
