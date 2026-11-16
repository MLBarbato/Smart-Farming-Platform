package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse.FazendaMapa;
import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse.PlantioAtual;
import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse.SensoresResumo;
import br.com.agricoladigital.smart_farming_api.dto.response.MapaFazendaResponse.TalhaoMapa;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.Leitura;
import br.com.agricoladigital.smart_farming_api.entity.Plantio;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.entity.Talhao;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoAlertasResponse;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import br.com.agricoladigital.smart_farming_api.repository.ContagemAlerta;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Monta, com poucas consultas, os dados do mapa de uma fazenda. */
@Service
@Transactional(readOnly = true)
public class MapaService {
    private final FazendaRepository fazendaRepository;
    private final TalhaoRepository talhaoRepository;
    private final PlantioRepository plantioRepository;
    private final SensorRepository sensorRepository;
    private final LeituraRepository leituraRepository;
    private final AlertaRepository alertaRepository;

    public MapaService(
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            AlertaRepository alertaRepository
    ) {
        this.fazendaRepository = fazendaRepository;
        this.talhaoRepository = talhaoRepository;
        this.plantioRepository = plantioRepository;
        this.sensorRepository = sensorRepository;
        this.leituraRepository = leituraRepository;
        this.alertaRepository = alertaRepository;
    }

    public MapaFazendaResponse mapaDaFazenda(Long fazendaId) {
        Fazenda fazenda = fazendaRepository.findById(fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fazenda não encontrada: " + fazendaId));
        List<Talhao> talhoes = talhaoRepository.findAllByFazenda_Id(fazendaId);

        Map<Long, Plantio> plantioPorTalhao = new HashMap<>();
        plantioRepository.buscarPorFazendaEStatus(fazendaId, StatusPlantio.EM_CULTIVO)
                .forEach(plantio -> plantioPorTalhao.putIfAbsent(plantio.getTalhao().getId(), plantio));

        List<Sensor> sensores = sensorRepository.listarPorFazenda(fazendaId);
        Map<Long, List<Sensor>> sensoresPorTalhao = sensores.stream()
                .collect(Collectors.groupingBy(sensor -> sensor.getDispositivo().getTalhao().getId()));

        Map<Long, Leitura> ultimaPorSensor = sensores.isEmpty() ? Map.of()
                : leituraRepository.ultimasPorDispositivos(sensores.stream().map(Sensor::getId).toList())
                .stream()
                .collect(Collectors.toMap(leitura -> leitura.getDispositivo().getId(), leitura -> leitura));

        List<ContagemAlerta> contagens = alertaRepository.contarPorTalhaoECriticidade(
                List.of(fazendaId), EnumSet.of(StatusAlerta.ABERTO, StatusAlerta.EM_ATENDIMENTO));
        Map<Long, List<ContagemAlerta>> alertasPorTalhao = contagens.stream()
                .collect(Collectors.groupingBy(ContagemAlerta::talhaoId));

        List<TalhaoMapa> resposta = talhoes.stream()
                .sorted(Comparator.comparing(Talhao::getIdentificacao))
                .map(talhao -> paraTalhaoMapa(
                        talhao,
                        plantioPorTalhao.get(talhao.getId()),
                        sensoresPorTalhao.getOrDefault(talhao.getId(), List.of()),
                        ultimaPorSensor,
                        ResumoAlertas.de(alertasPorTalhao.getOrDefault(talhao.getId(), List.of()))
                ))
                .toList();
        return new MapaFazendaResponse(paraFazendaMapa(fazenda), resposta, ResumoAlertas.de(contagens));
    }

    private static FazendaMapa paraFazendaMapa(Fazenda fazenda) {
        return new FazendaMapa(
                fazenda.getId(), fazenda.getNome(), fazenda.getLocalizacao(), fazenda.getLatitude(),
                fazenda.getLongitude(), fazenda.getCidade(), fazenda.getEstado(), fazenda.getAreaTotal()
        );
    }

    private static TalhaoMapa paraTalhaoMapa(
            Talhao talhao, Plantio plantio, List<Sensor> sensores, Map<Long, Leitura> ultimaPorSensor,
            ResumoAlertasResponse alertas
    ) {
        int ativos = (int) sensores.stream()
                .filter(sensor -> sensor.getDispositivo().getStatus() == StatusDispositivo.ATIVO)
                .count();
        List<LeituraResponse> leituras = sensores.stream()
                .filter(sensor -> ultimaPorSensor.containsKey(sensor.getId()))
                .sorted(Comparator.comparing(Sensor::getTipoSensor))
                .map(sensor -> paraLeitura(sensor, ultimaPorSensor.get(sensor.getId())))
                .toList();
        return new TalhaoMapa(
                talhao.getId(),
                talhao.getIdentificacao(),
                talhao.getAreaHectares(),
                TalhaoService.paraJson(talhao.getGeometria()),
                plantio == null ? null : paraPlantioAtual(plantio),
                new SensoresResumo(sensores.size(), ativos),
                leituras,
                alertas
        );
    }

    private static PlantioAtual paraPlantioAtual(Plantio plantio) {
        return new PlantioAtual(
                plantio.getId(), plantio.getCultura().getNome(), plantio.getTipoPlantio(), plantio.getSafra(),
                plantio.getStatus(), plantio.getDataPlantio(), plantio.getDataColheitaPrevista()
        );
    }

    private static LeituraResponse paraLeitura(Sensor sensor, Leitura leitura) {
        return new LeituraResponse(
                sensor.getId(), sensor.getTipoSensor(), leitura.getValor(), sensor.getUnidadeMedida(),
                leitura.getDataHora().atOffset(ZoneOffset.UTC), leitura.getOrigem()
        );
    }
}
