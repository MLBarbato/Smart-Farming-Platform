package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.LeituraResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse.CondicaoAtual;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse.CulturaEmCultivo;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse.Plantios;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse.ProdutividadeSafra;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse.Sensores;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoFazendaResponse.Talhoes;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoGeralResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoGeralResponse.FazendaCard;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoGeralResponse.Totais;
import br.com.agricoladigital.smart_farming_api.entity.EstacaoMeteorologica;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import br.com.agricoladigital.smart_farming_api.dto.response.ResumoAlertasResponse;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.Leitura;
import br.com.agricoladigital.smart_farming_api.entity.Plantio;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoEstacao;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.entity.Talhao;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import br.com.agricoladigital.smart_farming_api.repository.ContagemAlerta;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Indicadores para o dashboard, calculados sob demanda com uma consulta por tipo de dado
 * (talhões, plantios, sensores e últimas leituras) para o conjunto de fazendas pedido.
 *
 * <p>Critério provisório de "sensor sem leitura recente": sensor ATIVO cuja leitura mais recente
 * é mais antiga que {@code app.monitoramento.sensor-sem-leitura-minutos} (ou que nunca leu).
 */
@Service
@Transactional(readOnly = true)
public class ResumoService {
    private final FazendaRepository fazendaRepository;
    private final TalhaoRepository talhaoRepository;
    private final PlantioRepository plantioRepository;
    private final SensorRepository sensorRepository;
    private final LeituraRepository leituraRepository;
    private final EstacaoMeteorologicaRepository estacaoRepository;
    private final AlertaRepository alertaRepository;
    private final Duration janelaLeituraRecente;
    private final Clock clock;

    @Autowired
    public ResumoService(
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            AlertaRepository alertaRepository,
            @Value("${app.monitoramento.sensor-sem-leitura-minutos:30}") long minutosSemLeitura
    ) {
        this(fazendaRepository, talhaoRepository, plantioRepository, sensorRepository, leituraRepository,
                estacaoRepository, alertaRepository, minutosSemLeitura, Clock.systemUTC());
    }

    ResumoService(
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            AlertaRepository alertaRepository,
            long minutosSemLeitura,
            Clock clock
    ) {
        if (minutosSemLeitura < 1) {
            throw new IllegalArgumentException("app.monitoramento.sensor-sem-leitura-minutos deve ser positivo.");
        }
        this.fazendaRepository = fazendaRepository;
        this.talhaoRepository = talhaoRepository;
        this.plantioRepository = plantioRepository;
        this.sensorRepository = sensorRepository;
        this.leituraRepository = leituraRepository;
        this.estacaoRepository = estacaoRepository;
        this.alertaRepository = alertaRepository;
        this.janelaLeituraRecente = Duration.ofMinutes(minutosSemLeitura);
        this.clock = clock;
    }

    public ResumoFazendaResponse resumoDaFazenda(Long fazendaId) {
        Fazenda fazenda = fazendaRepository.findById(fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fazenda não encontrada: " + fazendaId));
        return resumir(List.of(fazenda)).get(0);
    }

    /** Fazendas do usuário; {@code usuarioId} nulo significa todas (administrador). */
    public ResumoGeralResponse resumoGeral(Long usuarioId) {
        List<Fazenda> fazendas = usuarioId == null
                ? fazendaRepository.findAll(Sort.by("nome"))
                : fazendaRepository.findAllByUsuario_Id(usuarioId);
        List<ResumoFazendaResponse> resumos = resumir(
                fazendas.stream().sorted(Comparator.comparing(Fazenda::getNome)).toList()
        );

        List<FazendaCard> cards = resumos.stream().map(ResumoService::paraCard).toList();
        Totais totais = new Totais(
                resumos.size(),
                resumos.stream().mapToInt(r -> r.talhoes().total()).sum(),
                resumos.stream().mapToDouble(r -> r.fazenda().areaTotal()).sum(),
                arredondar(resumos.stream().mapToDouble(r -> r.talhoes().areaEmCultivoHectares()).sum()),
                resumos.stream().mapToInt(r -> r.sensores().total()).sum(),
                resumos.stream().mapToInt(r -> r.sensores().ativos()).sum(),
                resumos.stream().mapToInt(r -> r.sensores().semLeituraRecente()).sum(),
                resumos.stream().mapToInt(r -> r.plantios().emCultivo()).sum(),
                ResumoAlertas.somar(resumos.stream().map(ResumoFazendaResponse::alertas).toList())
        );
        return new ResumoGeralResponse(totais, cards, (int) janelaLeituraRecente.toMinutes());
    }

    private List<ResumoFazendaResponse> resumir(List<Fazenda> fazendas) {
        if (fazendas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = fazendas.stream().map(Fazenda::getId).toList();
        Map<Long, List<Talhao>> talhoes = talhaoRepository.findAllByFazenda_IdIn(ids).stream()
                .collect(Collectors.groupingBy(t -> t.getFazenda().getId()));
        Map<Long, List<Plantio>> plantios = plantioRepository.buscarPorFazendas(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getTalhao().getFazenda().getId()));
        List<Sensor> todosSensores = sensorRepository.listarPorFazendas(ids);
        Map<Long, List<Sensor>> sensores = todosSensores.stream()
                .collect(Collectors.groupingBy(s -> s.getDispositivo().getTalhao().getFazenda().getId()));
        Map<Long, EstacaoMeteorologica> estacoes = estacaoRepository.listarDasFazendas(ids).stream()
                .collect(Collectors.toMap(e -> e.getDispositivo().getFazenda().getId(), e -> e));
        Map<Long, List<Sensor>> sensoresDaEstacao = estacoes.isEmpty() ? Map.of()
                : sensorRepository.listarDasEstacoes(estacoes.values().stream().map(EstacaoMeteorologica::getId).toList())
                .stream().collect(Collectors.groupingBy(s -> s.getEstacao().getId()));
        List<Long> idsComLeitura = new java.util.ArrayList<>(todosSensores.stream().map(Sensor::getId).toList());
        sensoresDaEstacao.values().forEach(lista -> lista.forEach(s -> idsComLeitura.add(s.getId())));
        Map<Long, Leitura> ultimas = idsComLeitura.isEmpty() ? Map.of()
                : leituraRepository.ultimasPorDispositivos(idsComLeitura)
                .stream()
                .collect(Collectors.toMap(l -> l.getDispositivo().getId(), l -> l));

        Map<Long, List<ContagemAlerta>> alertas = alertaRepository
                .contarPorTalhaoECriticidade(ids, EnumSet.of(StatusAlerta.ABERTO, StatusAlerta.EM_ATENDIMENTO))
                .stream().collect(Collectors.groupingBy(ContagemAlerta::fazendaId));

        LocalDateTime agora = LocalDateTime.now(clock).withNano(0);
        return fazendas.stream()
                .map(fazenda -> resumir(
                        fazenda,
                        talhoes.getOrDefault(fazenda.getId(), List.of()),
                        plantios.getOrDefault(fazenda.getId(), List.of()),
                        sensores.getOrDefault(fazenda.getId(), List.of()),
                        ultimas,
                        resumirEstacao(estacoes.get(fazenda.getId()), sensoresDaEstacao, ultimas, agora),
                        ResumoAlertas.de(alertas.getOrDefault(fazenda.getId(), List.of())),
                        agora
                ))
                .toList();
    }

    private ResumoFazendaResponse resumir(
            Fazenda fazenda,
            List<Talhao> talhoes,
            List<Plantio> plantios,
            List<Sensor> sensores,
            Map<Long, Leitura> ultimas,
            ResumoFazendaResponse.Estacao estacao,
            ResumoAlertasResponse alertas,
            LocalDateTime agora
    ) {
        List<Plantio> emCultivo = plantios.stream().filter(p -> p.getStatus() == StatusPlantio.EM_CULTIVO).toList();
        Set<Long> talhoesEmCultivo = emCultivo.stream().map(p -> p.getTalhao().getId()).collect(Collectors.toSet());

        Talhoes resumoTalhoes = new Talhoes(
                talhoes.size(),
                arredondar(talhoes.stream().mapToDouble(Talhao::getAreaHectares).sum()),
                talhoesEmCultivo.size(),
                arredondar(talhoes.stream().filter(t -> talhoesEmCultivo.contains(t.getId()))
                        .mapToDouble(Talhao::getAreaHectares).sum())
        );

        LocalDateTime limite = agora.minus(janelaLeituraRecente);
        List<Sensor> ativos = sensores.stream()
                .filter(s -> s.getDispositivo().getStatus() == StatusDispositivo.ATIVO).toList();
        int semLeitura = (int) ativos.stream().filter(s -> !recente(ultimas.get(s.getId()), limite)).count();
        Sensores resumoSensores = new Sensores(
                sensores.size(),
                ativos.size(),
                contar(sensores, StatusDispositivo.INATIVO),
                contar(sensores, StatusDispositivo.MANUTENCAO),
                semLeitura
        );

        Plantios resumoPlantios = new Plantios(
                contar(plantios, StatusPlantio.PLANEJADO),
                emCultivo.size(),
                contar(plantios, StatusPlantio.COLHIDO),
                contar(plantios, StatusPlantio.CANCELADO)
        );

        return new ResumoFazendaResponse(
                new ResumoFazendaResponse.Fazenda(
                        fazenda.getId(), fazenda.getNome(), fazenda.getCidade(), fazenda.getEstado(),
                        fazenda.getAreaTotal()
                ),
                resumoTalhoes,
                resumoSensores,
                resumoPlantios,
                culturasEmCultivo(emCultivo),
                condicoesAtuais(ativos, ultimas, limite),
                produtividade(plantios),
                estacao,
                alertas,
                (int) janelaLeituraRecente.toMinutes(),
                agora.atOffset(ZoneOffset.UTC)
        );
    }

    private static ResumoFazendaResponse.Estacao resumirEstacao(
            EstacaoMeteorologica estacao,
            Map<Long, List<Sensor>> sensoresPorEstacao,
            Map<Long, Leitura> ultimas,
            LocalDateTime agora
    ) {
        if (estacao == null) {
            return null;
        }
        List<LeituraResponse> leituras = sensoresPorEstacao.getOrDefault(estacao.getId(), List.of()).stream()
                .filter(sensor -> ultimas.containsKey(sensor.getId()))
                .sorted(Comparator.comparing(Sensor::getTipoSensor))
                .map(sensor -> {
                    Leitura leitura = ultimas.get(sensor.getId());
                    return new LeituraResponse(sensor.getId(), sensor.getTipoSensor(), leitura.getValor(),
                            sensor.getUnidadeMedida(), leitura.getDataHora().atOffset(ZoneOffset.UTC),
                            leitura.getOrigem());
                })
                .toList();
        return new ResumoFazendaResponse.Estacao(
                estacao.getId(),
                estacao.getDispositivo().getStatus(),
                SituacaoEstacao.de(estacao, agora),
                estacao.getUltimaSincronizacao() == null ? null : estacao.getUltimaSincronizacao().atOffset(ZoneOffset.UTC),
                leituras
        );
    }

    private static boolean recente(Leitura leitura, LocalDateTime limite) {
        return leitura != null && !leitura.getDataHora().isBefore(limite);
    }

    private static int contar(List<Sensor> sensores, StatusDispositivo status) {
        return (int) sensores.stream().filter(s -> s.getDispositivo().getStatus() == status).count();
    }

    private static int contar(List<Plantio> plantios, StatusPlantio status) {
        return (int) plantios.stream().filter(p -> p.getStatus() == status).count();
    }

    private static List<CulturaEmCultivo> culturasEmCultivo(List<Plantio> emCultivo) {
        Map<String, List<Plantio>> porCultura = emCultivo.stream()
                .collect(Collectors.groupingBy(p -> p.getCultura().getNome()));
        return porCultura.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new CulturaEmCultivo(
                        e.getKey(),
                        (int) e.getValue().stream().map(p -> p.getTalhao().getId()).distinct().count(),
                        arredondar(e.getValue().stream().mapToDouble(p -> p.getTalhao().getAreaHectares()).sum())
                ))
                .toList();
    }

    private List<CondicaoAtual> condicoesAtuais(
            List<Sensor> ativos, Map<Long, Leitura> ultimas, LocalDateTime limite
    ) {
        Map<VariavelMonitorada, List<Leitura>> porVariavel = new EnumMap<>(VariavelMonitorada.class);
        for (Sensor sensor : ativos) {
            Leitura leitura = ultimas.get(sensor.getId());
            if (recente(leitura, limite)) {
                porVariavel.computeIfAbsent(sensor.getTipoSensor(), v -> new java.util.ArrayList<>()).add(leitura);
            }
        }
        return porVariavel.entrySet().stream()
                .map(e -> {
                    DoubleSummaryStatistics estatistica = e.getValue().stream()
                            .mapToDouble(Leitura::getValor).summaryStatistics();
                    LocalDateTime maisRecente = e.getValue().stream().map(Leitura::getDataHora)
                            .max(Comparator.naturalOrder()).orElseThrow();
                    return new CondicaoAtual(
                            e.getKey(), e.getKey().getUnidade(), arredondar(estatistica.getAverage()),
                            estatistica.getMin(), estatistica.getMax(), e.getValue().size(),
                            maisRecente.atOffset(ZoneOffset.UTC)
                    );
                })
                .toList();
    }

    private static List<ProdutividadeSafra> produtividade(List<Plantio> plantios) {
        Map<String, List<Plantio>> porSafra = plantios.stream()
                .filter(p -> p.getStatus() == StatusPlantio.COLHIDO && p.getQuantidadeColhidaKg() != null)
                .collect(Collectors.groupingBy(Plantio::getSafra, LinkedHashMap::new, Collectors.toList()));
        return porSafra.entrySet().stream()
                .sorted(Map.Entry.<String, List<Plantio>>comparingByKey().reversed())
                .map(e -> {
                    double kg = e.getValue().stream().mapToDouble(Plantio::getQuantidadeColhidaKg).sum();
                    double area = e.getValue().stream().mapToDouble(p -> p.getTalhao().getAreaHectares()).sum();
                    return new ProdutividadeSafra(
                            e.getKey(), e.getValue().size(), arredondar(kg), arredondar(area),
                            area > 0 ? arredondar(kg / area) : 0
                    );
                })
                .toList();
    }

    private static ResumoGeralResponse.FazendaCard paraCard(ResumoFazendaResponse r) {
        return new FazendaCard(
                r.fazenda().id(), r.fazenda().nome(), r.fazenda().cidade(), r.fazenda().estado(),
                r.fazenda().areaTotal(), r.talhoes().total(), r.sensores().total(), r.sensores().ativos(),
                r.sensores().semLeituraRecente(), r.plantios().emCultivo(),
                r.culturasEmCultivo().stream().map(CulturaEmCultivo::cultura).toList(),
                r.alertas()
        );
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
