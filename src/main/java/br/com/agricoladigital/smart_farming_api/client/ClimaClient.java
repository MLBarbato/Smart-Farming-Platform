package br.com.agricoladigital.smart_farming_api.client;

import java.util.List;

/** Fonte externa de previsão do tempo. */
public interface ClimaClient {
    /** Código gravado em {@code fonte_api}. */
    String fonte();

    /**
     * Previsão diária para as coordenadas, a partir de hoje.
     *
     * @throws br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException se o provedor falhar ou responder
     *                                                      algo inválido
     */
    List<DiaPrevisto> buscarPrevisaoDiaria(double latitude, double longitude, int dias);

    /**
     * Condições horárias em UTC: as {@code horasPassadas} horas anteriores à hora atual e a própria
     * hora atual, em ordem cronológica.
     *
     * @throws br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException se o
     *         provedor falhar ou responder algo inválido
     */
    List<MedicaoHoraria> buscarMedicoesHorarias(double latitude, double longitude, int horasPassadas);
}
