package br.com.agricoladigital.smart_farming_api.repository;

import java.time.LocalDateTime;

/** Leituras de um dispositivo em um período: quantidade, primeira e última (UTC), mínimo, média e máximo. */
public record ResumoLeiturasSensor(
        Long dispositivoId,
        long quantidade,
        LocalDateTime primeira,
        LocalDateTime ultima,
        Double minimo,
        Double media,
        Double maximo
) {
}
