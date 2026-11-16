package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.client.DiaPrevisto;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.PrevisaoTempo;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PrevisaoTempoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Grava a previsão de uma fazenda numa única transação, substituindo o conjunto anterior. */
@Component
public class PrevisaoArmazenamento {
    private final PrevisaoTempoRepository previsaoRepository;
    private final FazendaRepository fazendaRepository;

    public PrevisaoArmazenamento(PrevisaoTempoRepository previsaoRepository, FazendaRepository fazendaRepository) {
        this.previsaoRepository = previsaoRepository;
        this.fazendaRepository = fazendaRepository;
    }

    @Transactional
    public void substituir(Long fazendaId, List<DiaPrevisto> dias, String fonte, LocalDateTime consultadaEmUtc) {
        Fazenda fazenda = fazendaRepository.findById(fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fazenda não encontrada: " + fazendaId));
        previsaoRepository.deleteAllByFazenda_Id(fazendaId);
        previsaoRepository.saveAll(dias.stream()
                .map(dia -> new PrevisaoTempo(
                        fazenda, dia.data(), dia.temperaturaMedia(), dia.temperaturaMinima(),
                        dia.temperaturaMaxima(), dia.precipitacaoMm(), dia.probabilidadePrecipitacao(),
                        fonte, consultadaEmUtc))
                .toList());
    }
}
