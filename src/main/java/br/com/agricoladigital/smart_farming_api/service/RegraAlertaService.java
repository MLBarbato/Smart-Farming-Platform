package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.RegraAlertaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.RegraAlertaResponse;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.RegraAlerta;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.RegraAlertaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RegraAlertaService {
    /** Teto do limiar de FALHA_SENSOR, em minutos (7 dias). */
    static final double MAXIMO_MINUTOS = 7 * 24 * 60;

    private final RegraAlertaRepository regraRepository;

    public RegraAlertaService(RegraAlertaRepository regraRepository) {
        this.regraRepository = regraRepository;
    }

    /** Regras ordenadas por evento e, dentro dele, da criticidade mais baixa para a mais alta. */
    public List<RegraAlertaResponse> listar(EventoAlerta evento, Boolean ativa) {
        List<RegraAlerta> regras = evento == null ? regraRepository.findAll() : regraRepository.findAllByEvento(evento);
        return regras.stream()
                .filter(regra -> ativa == null || ativa.equals(regra.getAtiva()))
                .sorted(Comparator.comparing(RegraAlerta::getEvento).thenComparing(RegraAlerta::getCriticidade))
                .map(RegraAlertaService::paraResponse)
                .toList();
    }

    public RegraAlertaResponse buscar(Long id) {
        return paraResponse(encontrar(id));
    }

    @Transactional
    public RegraAlertaResponse atualizar(Long id, RegraAlertaRequest request) {
        RegraAlerta regra = encontrar(id);
        validarJanela(regra, request.janelaHoras());
        validarLimiar(regra, request.limiar());
        validarOrdemEntreCriticidades(regra, request.limiar());
        regra.atualizar(request.limiar(), request.janelaHoras(), request.ativa(), request.descricao());
        return paraResponse(regra);
    }

    private static void validarJanela(RegraAlerta regra, Integer janelaHoras) {
        if (regra.getEvento().usaJanela() && janelaHoras == null) {
            throw new RegraDeNegocioException("A regra de " + regra.getEvento() + " exige janelaHoras.");
        }
        if (!regra.getEvento().usaJanela() && janelaHoras != null) {
            throw new RegraDeNegocioException("A regra de " + regra.getEvento() + " não usa janelaHoras.");
        }
    }

    private static void validarLimiar(RegraAlerta regra, double limiar) {
        if (!Double.isFinite(limiar)) {
            throw new RegraDeNegocioException("O limiar deve ser um número.");
        }
        if (regra.getVariavel() != null && !regra.getVariavel().aceita(limiar)) {
            throw new RegraDeNegocioException(
                    "Limiar fora da faixa válida de " + regra.getVariavel() + ": de "
                            + regra.getVariavel().getMinimo() + " a " + regra.getVariavel().getMaximo()
                            + " " + regra.getVariavel().getUnidade() + ".");
        }
        if (regra.getVariavel() == null && (limiar <= 0 || limiar > MAXIMO_MINUTOS)) {
            throw new RegraDeNegocioException("O limiar de " + regra.getEvento() + " deve estar entre 0 (exclusivo) e "
                    + (int) MAXIMO_MINUTOS + " minutos.");
        }
    }

    /**
     * Criticidades mais altas precisam de limiares mais severos: mais baixos quando a condição é
     * "menor que", mais altos quando é "maior que", sem empates (um empate faria um nível nunca
     * ser alcançado).
     */
    private void validarOrdemEntreCriticidades(RegraAlerta regra, double novoLimiar) {
        boolean piorQuandoMenor = regra.getOperador().piorQuandoMenor();
        for (RegraAlerta outra : regraRepository.findAllByEvento(regra.getEvento())) {
            if (outra.getId().equals(regra.getId())) {
                continue;
            }
            boolean outraMaisSevera = outra.getCriticidade().compareTo(regra.getCriticidade()) > 0;
            boolean coerente = piorQuandoMenor == outraMaisSevera
                    ? outra.getLimiar() < novoLimiar     // a mais severa deve ser mais baixa
                    : outra.getLimiar() > novoLimiar;    // a mais severa deve ser mais alta
            if (!coerente) {
                throw new RegraDeNegocioException(
                        "O limiar deve manter a ordem de severidade entre as criticidades de "
                                + regra.getEvento() + " (conflita com " + outra.getCriticidade() + ": "
                                + outra.getLimiar() + ").");
            }
        }
    }

    private RegraAlerta encontrar(Long id) {
        return regraRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Regra de alerta não encontrada: " + id));
    }

    private static RegraAlertaResponse paraResponse(RegraAlerta regra) {
        return new RegraAlertaResponse(
                regra.getId(), regra.getEvento(), regra.getCriticidade(), regra.getOperador(), regra.getVariavel(),
                regra.getLimiar(), regra.getUnidade(), regra.getJanelaHoras(), regra.getAtiva(), regra.getDescricao()
        );
    }
}
