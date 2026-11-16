package br.com.agricoladigital.smart_farming_api.entity;

import java.time.Duration;
import java.time.LocalDateTime;

public enum SituacaoEstacao {
    /** Sincronizada há menos que o dobro da frequência de coleta. */
    ATUAL,
    /** A última sincronização bem-sucedida passou de duas vezes a frequência de coleta. */
    DESATUALIZADA,
    /** Criada, mas nunca sincronizada com sucesso. */
    SEM_SINCRONIZACAO,
    /** Estação desativada: não recebe leituras. */
    INATIVA;

    public static SituacaoEstacao de(EstacaoMeteorologica estacao, LocalDateTime agoraUtc) {
        if (estacao.getDispositivo().getStatus() != StatusDispositivo.ATIVO) {
            return INATIVA;
        }
        if (estacao.getUltimaSincronizacao() == null) {
            return SEM_SINCRONIZACAO;
        }
        Duration limite = Duration.ofMinutes(2L * estacao.getFrequenciaColeta());
        return Duration.between(estacao.getUltimaSincronizacao(), agoraUtc).compareTo(limite) > 0
                ? DESATUALIZADA : ATUAL;
    }
}
