package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.Alerta;
import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import br.com.agricoladigital.smart_farming_api.entity.Talhao;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Aplica o resultado de uma avaliação ao alerta do talhão: cria, sobe de criticidade, registra a
 * normalização ou fecha sozinho. Participa da transação de quem chama.
 */
@Component
public class ConciliadorAlertas {
    public enum Acao { NENHUMA, CRIADO, ESCALADO, NORMALIZADO, RESOLVIDO_AUTOMATICAMENTE, REABERTURA_ADIADA }

    private final AlertaRepository alertaRepository;

    public ConciliadorAlertas(AlertaRepository alertaRepository) {
        this.alertaRepository = alertaRepository;
    }

    /**
     * @param aberto         alerta ABERTO ou EM_ATENDIMENTO do talhão e evento, se houver
     * @param fechadaRecente maior criticidade entre os alertas do talhão e evento fechados dentro do
     *                       período de espera para reabertura, se houver
     */
    public Acao conciliar(
            Talhao talhao,
            EventoAlerta evento,
            ResultadoAvaliacao resultado,
            Alerta aberto,
            CriticidadeAlerta fechadaRecente,
            LocalDateTime agora
    ) {
        return switch (resultado.tipo()) {
            case SEM_DADOS -> Acao.NENHUMA;
            case NORMAL -> normalizar(aberto, agora);
            case CONDICAO -> condicao(talhao, resultado, aberto, fechadaRecente, agora);
        };
    }

    private Acao normalizar(Alerta aberto, LocalDateTime agora) {
        if (aberto == null) {
            return Acao.NENHUMA;
        }
        if (aberto.getStatus() == StatusAlerta.ABERTO) {
            aberto.resolverAutomaticamente(agora);
            return Acao.RESOLVIDO_AUTOMATICAMENTE;
        }
        if (aberto.getNormalizadoEm() == null) {
            aberto.registrarNormalizacao(agora);
            return Acao.NORMALIZADO;
        }
        return Acao.NENHUMA;
    }

    private Acao condicao(
            Talhao talhao, ResultadoAvaliacao r, Alerta aberto, CriticidadeAlerta fechadaRecente, LocalDateTime agora
    ) {
        if (aberto != null) {
            if (r.regra().getCriticidade().compareTo(aberto.getCriticidade()) > 0) {
                aberto.escalar(r.regra(), r.leitura(), r.mensagem(), agora);
                return Acao.ESCALADO;
            }
            if (aberto.getNormalizadoEm() != null) {
                aberto.retomar(agora);
            }
            return Acao.NENHUMA;
        }
        if (fechadaRecente != null && r.regra().getCriticidade().compareTo(fechadaRecente) <= 0) {
            return Acao.REABERTURA_ADIADA;
        }
        // saveAndFlush: uma colisão com o índice único (avaliações simultâneas) aparece aqui.
        alertaRepository.saveAndFlush(new Alerta(r.regra(), talhao, r.leitura(), agora, r.mensagem()));
        return Acao.CRIADO;
    }
}
