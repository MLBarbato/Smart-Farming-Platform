package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.Alerta;
import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;
import br.com.agricoladigital.smart_farming_api.entity.EstacaoMeteorologica;
import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.Leitura;
import br.com.agricoladigital.smart_farming_api.entity.OrigemUmidade;
import br.com.agricoladigital.smart_farming_api.entity.Plantio;
import br.com.agricoladigital.smart_farming_api.entity.RecomendacaoIrrigacao;
import br.com.agricoladigital.smart_farming_api.entity.ResolucaoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoIrrigacao;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.entity.TipoRelatorio;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import br.com.agricoladigital.smart_farming_api.repository.RecomendacaoIrrigacaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.ResumoLeiturasSensor;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gera os relatórios de uma fazenda em CSV. O período é dado em datas locais (fuso de Brasília), com início e
 * fim incluídos; os instantes do banco (UTC) são convertidos para esse fuso nas células.
 */
@Service
public class RelatorioService {
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final int DIAS_PADRAO = 30;
    static final int MAXIMO_DIAS = 366;

    /** Variáveis da estação virtual, na ordem das colunas do relatório de clima. */
    private static final List<VariavelMonitorada> COLUNAS_CLIMA = List.of(
            VariavelMonitorada.TEMPERATURA_AR, VariavelMonitorada.UMIDADE_AR, VariavelMonitorada.VELOCIDADE_VENTO,
            VariavelMonitorada.PRECIPITACAO, VariavelMonitorada.UMIDADE_SOLO);

    private final FazendaRepository fazendaRepository;
    private final RecomendacaoIrrigacaoRepository recomendacaoRepository;
    private final AlertaRepository alertaRepository;
    private final PlantioRepository plantioRepository;
    private final SensorRepository sensorRepository;
    private final LeituraRepository leituraRepository;
    private final EstacaoMeteorologicaRepository estacaoRepository;
    private final Duration semLeituraRecente;
    private final Clock clock;

    /** O arquivo gerado: nome sugerido para o download e conteúdo em UTF-8. */
    public record Arquivo(String nome, byte[] conteudo) {
    }

    @Autowired
    public RelatorioService(
            FazendaRepository fazendaRepository,
            RecomendacaoIrrigacaoRepository recomendacaoRepository,
            AlertaRepository alertaRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            @Value("${app.monitoramento.sensor-sem-leitura-minutos:30}") long minutosSemLeitura
    ) {
        this(fazendaRepository, recomendacaoRepository, alertaRepository, plantioRepository, sensorRepository,
                leituraRepository, estacaoRepository, minutosSemLeitura, Clock.systemUTC());
    }

    RelatorioService(
            FazendaRepository fazendaRepository,
            RecomendacaoIrrigacaoRepository recomendacaoRepository,
            AlertaRepository alertaRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            long minutosSemLeitura,
            Clock clock
    ) {
        this.fazendaRepository = fazendaRepository;
        this.recomendacaoRepository = recomendacaoRepository;
        this.alertaRepository = alertaRepository;
        this.plantioRepository = plantioRepository;
        this.sensorRepository = sensorRepository;
        this.leituraRepository = leituraRepository;
        this.estacaoRepository = estacaoRepository;
        this.semLeituraRecente = Duration.ofMinutes(minutosSemLeitura);
        this.clock = clock;
    }

    /**
     * Gera o relatório do tipo pedido. Sem datas, usa os últimos {@value #DIAS_PADRAO} dias até hoje.
     *
     * @throws RegraDeNegocioException se o início for posterior ao fim ou o período passar de {@value #MAXIMO_DIAS} dias
     * @throws RecursoNaoEncontradoException se a fazenda não existir ou, no relatório de clima, não tiver estação
     */
    @Transactional(readOnly = true)
    public Arquivo gerar(Long fazendaId, TipoRelatorio tipo, LocalDate de, LocalDate ate) {
        if (fazendaRepository.findById(fazendaId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Fazenda não encontrada.");
        }
        LocalDate fim = ate != null ? ate : LocalDate.now(clock.withZone(FUSO));
        LocalDate inicio = de != null ? de : fim.minusDays(DIAS_PADRAO - 1L);
        if (inicio.isAfter(fim)) {
            throw new RegraDeNegocioException("O início do período não pode ser posterior ao fim.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > MAXIMO_DIAS) {
            throw new RegraDeNegocioException("O período do relatório pode ter no máximo " + MAXIMO_DIAS + " dias.");
        }
        Periodo periodo = new Periodo(inicio, fim, utc(inicio), utc(fim.plusDays(1)));
        Csv csv = new Csv(FUSO);
        switch (tipo) {
            case IRRIGACAO -> irrigacao(csv, fazendaId, periodo);
            case CLIMA -> clima(csv, fazendaId, periodo);
            case SENSORES -> sensores(csv, fazendaId, periodo);
            case ALERTAS -> alertas(csv, fazendaId, periodo);
            case PRODUTIVIDADE -> produtividade(csv, fazendaId, periodo);
        }
        String nome = "relatorio-%s-fazenda-%d-%s-a-%s.csv".formatted(tipo.getArquivo(), fazendaId, inicio, fim);
        return new Arquivo(nome, csv.bytes());
    }

    /** Datas locais do período e os limites em UTC: [deUtc, ateUtc). */
    private record Periodo(LocalDate de, LocalDate ate, LocalDateTime deUtc, LocalDateTime ateUtc) {
    }

    private static LocalDateTime utc(LocalDate dataLocal) {
        return dataLocal.atStartOfDay(FUSO).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    // ---- relatórios ---------------------------------------------------------------------------

    private void irrigacao(Csv csv, Long fazendaId, Periodo p) {
        csv.linha("Data/hora do cálculo", "Talhão", "Cultura", "Situação", "Volume recomendado (mm)",
                "Umidade do solo (%)", "Origem da umidade", "Umidade mínima (%)", "Umidade ideal (%)",
                "Profundidade da raiz (cm)", "Déficit (mm)", "Chuva prevista (mm)", "Chuva efetiva (mm)",
                "Temperatura máxima prevista (°C)", "Válida até", "Dados faltantes", "Justificativa");
        for (RecomendacaoIrrigacao r : recomendacaoRepository.buscarNoPeriodo(fazendaId, p.deUtc(), p.ateUtc())) {
            csv.linha(r.getDataHora(), r.getTalhao().getIdentificacao(), r.getCulturaNome(),
                    rotulo(r.getSituacao()), r.getVolumeRecomendadoMm(), r.getUmidadeAtual(),
                    r.getOrigemUmidade() == null ? null : rotulo(r.getOrigemUmidade()), r.getUmidadeMinima(),
                    r.getUmidadeIdeal(), r.getProfundidadeRaizCm(), r.getDeficitMm(), r.getChuvaPrevistaMm(),
                    r.getChuvaEfetivaMm(), r.getTemperaturaMaximaPrevista(), r.getValidoAte(),
                    String.join(", ", r.listaDadosFaltantes()), r.getJustificativa());
        }
    }

    private void clima(Csv csv, Long fazendaId, Periodo p) {
        EstacaoMeteorologica estacao = estacaoRepository.buscarDaFazenda(fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "A fazenda não tem estação virtual; crie-a na tela de Clima para ter o histórico."));
        Map<Long, VariavelMonitorada> variavelDoSensor = sensorRepository.listarDaEstacao(estacao.getId()).stream()
                .filter(s -> COLUNAS_CLIMA.contains(s.getTipoSensor()))
                .collect(Collectors.toMap(Sensor::getId, Sensor::getTipoSensor));

        Object[] cabecalho = new Object[COLUNAS_CLIMA.size() + 2];
        cabecalho[0] = "Data/hora";
        for (int i = 0; i < COLUNAS_CLIMA.size(); i++) {
            VariavelMonitorada v = COLUNAS_CLIMA.get(i);
            cabecalho[i + 1] = "%s (%s)".formatted(rotulo(v), v.getUnidade());
        }
        cabecalho[cabecalho.length - 1] = "Fonte";
        csv.linha(cabecalho);
        if (variavelDoSensor.isEmpty()) {
            return;
        }

        Map<LocalDateTime, Map<VariavelMonitorada, Double>> porHora = new TreeMap<>();
        for (Leitura l : leituraRepository.buscarNoPeriodo(variavelDoSensor.keySet(), p.deUtc(), p.ateUtc())) {
            porHora.computeIfAbsent(l.getDataHora(), h -> new EnumMap<>(VariavelMonitorada.class))
                    .put(variavelDoSensor.get(l.getDispositivo().getId()), l.getValor());
        }
        porHora.forEach((hora, valores) -> {
            Object[] linha = new Object[COLUNAS_CLIMA.size() + 2];
            linha[0] = hora;
            for (int i = 0; i < COLUNAS_CLIMA.size(); i++) {
                linha[i + 1] = valores.get(COLUNAS_CLIMA.get(i));
            }
            linha[linha.length - 1] = PrevisaoService.ATRIBUICAO;
            csv.linha(linha);
        });
    }

    private void sensores(Csv csv, Long fazendaId, Periodo p) {
        csv.linha("Código", "Talhão", "Variável", "Unidade", "Estado", "Instalação", "Leituras no período",
                "Primeira leitura no período", "Última leitura no período", "Mínimo", "Média", "Máximo",
                "Última leitura recebida", "Comunicação agora");
        List<Sensor> sensores = sensorRepository.listarPorFazenda(fazendaId).stream()
                .sorted(Comparator.comparing((Sensor s) -> s.getDispositivo().getTalhao().getIdentificacao())
                        .thenComparing(s -> s.getDispositivo().getCodigoIdentificacao()))
                .toList();
        if (sensores.isEmpty()) {
            return;
        }
        List<Long> ids = sensores.stream().map(Sensor::getId).toList();
        Map<Long, ResumoLeiturasSensor> resumos = leituraRepository.resumirNoPeriodo(ids, p.deUtc(), p.ateUtc())
                .stream().collect(Collectors.toMap(ResumoLeiturasSensor::dispositivoId, Function.identity()));
        Map<Long, LocalDateTime> ultimas = leituraRepository.ultimasPorDispositivos(ids).stream()
                .collect(Collectors.toMap(l -> l.getDispositivo().getId(), Leitura::getDataHora, (a, b) -> a));
        LocalDateTime agora = LocalDateTime.now(clock);

        for (Sensor s : sensores) {
            ResumoLeiturasSensor r = resumos.get(s.getId());
            LocalDateTime ultima = ultimas.get(s.getId());
            csv.linha(s.getDispositivo().getCodigoIdentificacao(), s.getDispositivo().getTalhao().getIdentificacao(),
                    rotulo(s.getTipoSensor()), s.getTipoSensor().getUnidade(), rotulo(s.getDispositivo().getStatus()),
                    s.getDispositivo().getDataInstalacao(), r == null ? 0 : r.quantidade(),
                    r == null ? null : r.primeira(), r == null ? null : r.ultima(),
                    r == null ? null : r.minimo(), r == null ? null : arredondar(r.media()),
                    r == null ? null : r.maximo(), ultima, comunicacao(s, ultima, agora));
        }
    }

    private String comunicacao(Sensor sensor, LocalDateTime ultima, LocalDateTime agora) {
        if (sensor.getDispositivo().getStatus() != StatusDispositivo.ATIVO) {
            return "Não monitorado";
        }
        if (ultima == null) {
            return "Sem leituras";
        }
        return Duration.between(ultima, agora).compareTo(semLeituraRecente) > 0 ? "Sem leitura recente" : "Em dia";
    }

    private void alertas(Csv csv, Long fazendaId, Periodo p) {
        csv.linha("Abertura", "Talhão", "Evento", "Criticidade", "Situação", "Mensagem", "Responsável",
                "Assumido em", "Fechado em", "Tipo de resolução", "Nota", "Horas até o fechamento");
        for (Alerta a : alertaRepository.buscarNoPeriodo(fazendaId, p.deUtc(), p.ateUtc())) {
            Double horas = a.getResolvidoEm() == null ? null
                    : arredondar(Duration.between(a.getDataHora(), a.getResolvidoEm()).toMinutes() / 60.0);
            csv.linha(a.getDataHora(), a.getTalhao().getIdentificacao(), rotulo(a.getEvento()),
                    rotulo(a.getCriticidade()), rotulo(a.getStatus()), a.getMensagem(),
                    a.getAtendente() == null ? null : a.getAtendente().getNome(), a.getAtendidoEm(),
                    a.getResolvidoEm(), a.getResolucao() == null ? null : rotulo(a.getResolucao()), a.getNota(), horas);
        }
    }

    private void produtividade(Csv csv, Long fazendaId, Periodo p) {
        csv.linha("Talhão", "Área do talhão (ha)", "Cultura", "Safra", "Tipo de plantio", "Situação",
                "Data de plantio", "Colheita prevista", "Colheita real", "Quantidade colhida (kg)",
                "Produtividade (kg/ha)");
        for (Plantio pl : plantioRepository.buscarNoPeriodo(fazendaId, p.de(), p.ate())) {
            Double area = pl.getTalhao().getAreaHectares();
            Double produtividade = pl.getQuantidadeColhidaKg() == null || area == null || area <= 0 ? null
                    : arredondar(pl.getQuantidadeColhidaKg() / area);
            csv.linha(pl.getTalhao().getIdentificacao(), area, pl.getCultura().getNome(), pl.getSafra(),
                    pl.getTipoPlantio(), rotulo(pl.getStatus()), pl.getDataPlantio(), pl.getDataColheitaPrevista(),
                    pl.getDataColheitaReal(), pl.getQuantidadeColhidaKg(), produtividade);
        }
    }

    // ---- rótulos em português -----------------------------------------------------------------

    private static Double arredondar(Double valor) {
        return valor == null ? null : Math.round(valor * 100) / 100.0;
    }

    static String rotulo(SituacaoIrrigacao s) {
        return switch (s) {
            case IRRIGAR -> "Irrigar";
            case NAO_IRRIGAR -> "Não irrigar";
            case AGUARDAR_CHUVA -> "Aguardar chuva";
            case SEM_DADOS_SUFICIENTES -> "Sem dados suficientes";
        };
    }

    static String rotulo(OrigemUmidade o) {
        return switch (o) {
            case SENSOR_TALHAO -> "Sensor do talhão";
            case ESTACAO -> "Estação virtual (modelo)";
        };
    }

    static String rotulo(EventoAlerta e) {
        return switch (e) {
            case SECA -> "Seca";
            case EXCESSO_PRECIPITACAO -> "Excesso de precipitação";
            case RISCO_GEADA -> "Risco de geada";
            case FALHA_SENSOR -> "Falha de sensor";
        };
    }

    static String rotulo(CriticidadeAlerta c) {
        return switch (c) {
            case BAIXA -> "Baixa";
            case MEDIA -> "Média";
            case ALTA -> "Alta";
        };
    }

    static String rotulo(StatusAlerta s) {
        return switch (s) {
            case ABERTO -> "Aberto";
            case EM_ATENDIMENTO -> "Em atendimento";
            case RESOLVIDO -> "Resolvido";
            case DISPENSADO -> "Dispensado";
        };
    }

    static String rotulo(ResolucaoAlerta r) {
        return switch (r) {
            case MANUAL -> "Manual";
            case AUTOMATICA -> "Automática (condição normalizou)";
        };
    }

    static String rotulo(StatusPlantio s) {
        return switch (s) {
            case PLANEJADO -> "Planejado";
            case EM_CULTIVO -> "Em cultivo";
            case COLHIDO -> "Colhido";
            case CANCELADO -> "Cancelado";
        };
    }

    static String rotulo(StatusDispositivo s) {
        return switch (s) {
            case ATIVO -> "Ativo";
            case INATIVO -> "Inativo";
            case MANUTENCAO -> "Em manutenção";
        };
    }

    static String rotulo(VariavelMonitorada v) {
        return switch (v) {
            case TEMPERATURA_AR -> "Temperatura do ar";
            case UMIDADE_AR -> "Umidade do ar";
            case UMIDADE_SOLO -> "Umidade do solo";
            case LUMINOSIDADE -> "Luminosidade";
            case PH_SOLO -> "pH do solo";
            case VELOCIDADE_VENTO -> "Velocidade do vento";
            case PRECIPITACAO -> "Precipitação";
            case TEMPERATURA_SOLO -> "Temperatura do solo";
            case CONDUTIVIDADE_ELETRICA -> "Condutividade elétrica";
        };
    }
}
