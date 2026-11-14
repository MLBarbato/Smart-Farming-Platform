package br.com.agricoladigital.smart_farming_api.entity;

import java.util.EnumSet;
import java.util.Set;

public enum StatusPlantio {
    PLANEJADO,
    EM_CULTIVO,
    COLHIDO,
    CANCELADO;

    public Set<StatusPlantio> proximos() {
        return switch (this) {
            case PLANEJADO -> EnumSet.of(EM_CULTIVO, CANCELADO);
            case EM_CULTIVO -> EnumSet.of(COLHIDO, CANCELADO);
            case COLHIDO, CANCELADO -> EnumSet.noneOf(StatusPlantio.class);
        };
    }

    public boolean podeIrPara(StatusPlantio destino) {
        return proximos().contains(destino);
    }

    public boolean finalizado() {
        return this == COLHIDO || this == CANCELADO;
    }
}
