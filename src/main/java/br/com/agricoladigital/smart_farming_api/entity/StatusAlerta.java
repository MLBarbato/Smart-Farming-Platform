package br.com.agricoladigital.smart_farming_api.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados do atendimento. ABERTO pode ser assumido (EM_ATENDIMENTO), resolvido ou dispensado;
 * EM_ATENDIMENTO pode ser resolvido ou dispensado; RESOLVIDO e DISPENSADO são finais.
 */
public enum StatusAlerta {
    ABERTO,
    EM_ATENDIMENTO,
    RESOLVIDO,
    DISPENSADO;

    public Set<StatusAlerta> proximos() {
        return switch (this) {
            case ABERTO -> EnumSet.of(EM_ATENDIMENTO, RESOLVIDO, DISPENSADO);
            case EM_ATENDIMENTO -> EnumSet.of(RESOLVIDO, DISPENSADO);
            case RESOLVIDO, DISPENSADO -> EnumSet.noneOf(StatusAlerta.class);
        };
    }

    public boolean aberto() {
        return this == ABERTO || this == EM_ATENDIMENTO;
    }
}
