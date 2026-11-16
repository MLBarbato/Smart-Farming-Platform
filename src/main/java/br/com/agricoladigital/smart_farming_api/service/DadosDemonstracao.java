package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.*;
import br.com.agricoladigital.smart_farming_api.dto.response.*;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Carrega um conjunto de dados de demonstração pelos próprios serviços da aplicação (portanto, com as
 * mesmas validações): usuários, fazendas com talhões e polígonos, culturas com parâmetros de irrigação,
 * plantios (inclusive uma safra colhida), sensores, 72 h de leituras simuladas, previsão e estação
 * (se o Open-Meteo estiver acessível), alertas e recomendações de irrigação.
 *
 * <p>Só roda com {@code app.demo.enabled=true} ({@code DEMO_DADOS_ENABLED}) e exige {@code DEMO_SENHA}
 * (a senha dos usuários de demonstração; não há senha no repositório). É idempotente: se o usuário de
 * demonstração já existir, não faz nada. Não use em produção.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DadosDemonstracao implements ApplicationRunner {
    public static final String EMAIL_ANA = "ana.demo@example.com";
    public static final String EMAIL_BRUNO = "bruno.demo@example.com";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final double METROS_POR_GRAU = 111_320.0;
    private static final Duration HISTORICO = Duration.ofHours(72);
    private static final Duration PASSO = Duration.ofMinutes(15);
    /** Janela, no fim do histórico, em que os cenários de seca e de sensor parado se aplicam. */
    private static final Duration JANELA_CENARIO = Duration.ofHours(6);

    /** Há quanto tempo o sensor parado enviou a última leitura (acima da janela de 3 h, com folga). */
    private static final Duration PARADO_HA = Duration.ofMinutes(210);

    private enum Situacao { NORMAL, SECA_ALTA, SECA_BAIXA, SENSOR_PARADO }

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final CulturaService culturaService;
    private final FazendaService fazendaService;
    private final TalhaoService talhaoService;
    private final PlantioService plantioService;
    private final SensorService sensorService;
    private final SensorRepository sensorRepository;
    private final LeituraService leituraService;
    private final PrevisaoService previsaoService;
    private final EstacaoService estacaoService;
    private final AvaliadorAlertas avaliador;
    private final RecomendadorIrrigacao recomendador;
    private final AlertaRepository alertaRepository;
    private final AlertaService alertaService;
    private final String senha;
    private final Clock clock;
    private final GeradorLeituraSimulada gerador = new GeradorLeituraSimulada(new Random(20261116L));

    @Autowired
    public DadosDemonstracao(
            UsuarioService usuarioService, UsuarioRepository usuarioRepository, CulturaService culturaService,
            FazendaService fazendaService, TalhaoService talhaoService, PlantioService plantioService,
            SensorService sensorService, SensorRepository sensorRepository, LeituraService leituraService,
            PrevisaoService previsaoService, EstacaoService estacaoService, AvaliadorAlertas avaliador,
            RecomendadorIrrigacao recomendador, AlertaRepository alertaRepository, AlertaService alertaService,
            @Value("${app.demo.password:}") String senha
    ) {
        this(usuarioService, usuarioRepository, culturaService, fazendaService, talhaoService, plantioService,
                sensorService, sensorRepository, leituraService, previsaoService, estacaoService, avaliador,
                recomendador, alertaRepository, alertaService, senha, Clock.systemUTC());
    }

    DadosDemonstracao(
            UsuarioService usuarioService, UsuarioRepository usuarioRepository, CulturaService culturaService,
            FazendaService fazendaService, TalhaoService talhaoService, PlantioService plantioService,
            SensorService sensorService, SensorRepository sensorRepository, LeituraService leituraService,
            PrevisaoService previsaoService, EstacaoService estacaoService, AvaliadorAlertas avaliador,
            RecomendadorIrrigacao recomendador, AlertaRepository alertaRepository, AlertaService alertaService,
            String senha, Clock clock
    ) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.culturaService = culturaService;
        this.fazendaService = fazendaService;
        this.talhaoService = talhaoService;
        this.plantioService = plantioService;
        this.sensorService = sensorService;
        this.sensorRepository = sensorRepository;
        this.leituraService = leituraService;
        this.previsaoService = previsaoService;
        this.estacaoService = estacaoService;
        this.avaliador = avaliador;
        this.recomendador = recomendador;
        this.alertaRepository = alertaRepository;
        this.alertaService = alertaService;
        this.senha = senha == null ? "" : senha;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        carregar();
    }

    /** Resultado resumido, útil para o log e para os testes. */
    public record Resumo(boolean carregado, int fazendas, int talhoes, int sensores, int leituras, int alertasAbertos,
                         boolean previsaoObtida) {
    }

    public Resumo carregar() {
        if (senha.length() < 8) {
            log.warn("Dados de demonstração não carregados: defina DEMO_SENHA (mínimo de 8 caracteres).");
            return new Resumo(false, 0, 0, 0, 0, 0, false);
        }
        if (usuarioRepository.existsByEmail(EMAIL_ANA)) {
            log.info("Dados de demonstração já existem ({}); nada a fazer.", EMAIL_ANA);
            return new Resumo(false, 0, 0, 0, 0, 0, false);
        }
        log.info("Carregando dados de demonstração...");
        UsuarioResponse ana = usuarioService.criar(new CriarUsuarioRequest("Ana Silva", EMAIL_ANA, senha, PerfilUsuario.AGRICULTOR));
        UsuarioResponse bruno = usuarioService.criar(new CriarUsuarioRequest("Bruno Costa", EMAIL_BRUNO, senha, PerfilUsuario.AGRICULTOR));

        CulturaResponse soja = cultura("Soja", 120, 30.0, 38.0, 30);
        CulturaResponse milho = cultura("Milho", 130, 32.0, 40.0, 50);
        CulturaResponse feijao = cultura("Feijão", 90, 35.0, 42.0, 25);

        List<Long> fazendas = new ArrayList<>();
        int talhoes = 0;
        int sensores = 0;
        int leituras = 0;
        boolean previsao = false;

        // Fazenda Santa Maria (Rio Verde, GO): uma seca grave no talhão 02 e uma safra já colhida no 01.
        FazendaResponse santaMaria = fazenda(ana.id(), "Fazenda Santa Maria", "Zona rural de Rio Verde", 1240.0,
                -17.7923, -50.9192, "Rio Verde", "GO");
        fazendas.add(santaMaria.id());
        List<TalhaoResponse> sm = talhoes(santaMaria, 4, 2);
        Hoje h = hoje();
        colhido(santaMaria, sm.get(0), soja, h.data.minusDays(300), h.data.minusDays(185), 3.6);
        emCultivo(santaMaria, sm.get(0), soja, h.data.minusDays(45));
        emCultivo(santaMaria, sm.get(1), milho, h.data.minusDays(60));
        colhido(santaMaria, sm.get(2), milho, h.data.minusDays(250), h.data.minusDays(120), 8.4);
        planejado(santaMaria, sm.get(3), feijao, h.data.plusDays(20));
        leituras += sensores(santaMaria, "SM", sm.get(0), Situacao.NORMAL);
        leituras += sensores(santaMaria, "SM", sm.get(1), Situacao.SECA_ALTA);
        sensores += 6;
        talhoes += sm.size();
        previsao |= previsaoEEstacao(santaMaria.id());

        // Fazenda Boa Esperança (Sorriso, MT): seca leve no 01 e sensores do 02 sem enviar leituras.
        FazendaResponse boaEsperanca = fazenda(ana.id(), "Fazenda Boa Esperança", "Zona rural de Sorriso", 860.0,
                -12.5436, -55.7211, "Sorriso", "MT");
        fazendas.add(boaEsperanca.id());
        List<TalhaoResponse> be = talhoes(boaEsperanca, 3, 2);
        emCultivo(boaEsperanca, be.get(0), soja, h.data.minusDays(38));
        emCultivo(boaEsperanca, be.get(1), milho, h.data.minusDays(52));
        colhido(boaEsperanca, be.get(2), soja, h.data.minusDays(280), h.data.minusDays(160), 3.9);
        leituras += sensores(boaEsperanca, "BE", be.get(0), Situacao.SECA_BAIXA);
        leituras += sensores(boaEsperanca, "BE", be.get(1), Situacao.SENSOR_PARADO);
        sensores += 6;
        talhoes += be.size();
        previsao |= previsaoEEstacao(boaEsperanca.id());

        // Sítio Água Limpa (Cristalina, GO), de outro agricultor: tudo normal (mostra o isolamento entre usuários).
        FazendaResponse aguaLimpa = fazenda(bruno.id(), "Sítio Água Limpa", "Zona rural de Cristalina", 320.0,
                -16.7684, -47.6139, "Cristalina", "GO");
        fazendas.add(aguaLimpa.id());
        List<TalhaoResponse> al = talhoes(aguaLimpa, 2, 2);
        emCultivo(aguaLimpa, al.get(0), feijao, h.data.minusDays(30));
        leituras += sensores(aguaLimpa, "AL", al.get(0), Situacao.NORMAL);
        sensores += 3;
        talhoes += al.size();
        previsao |= previsaoEEstacao(aguaLimpa.id());

        for (Long id : fazendas) {
            try {
                avaliador.avaliarFazenda(id);
                recomendador.calcularFazenda(id);
            } catch (RuntimeException erro) {
                log.warn("Demonstração: alertas/recomendações da fazenda {} não calculados: {}", id, erro.getMessage());
            }
        }
        atenderUmAlerta(santaMaria.id(), ana.id());
        int abertos = (int) alertaRepository.findAll().stream().filter(a -> a.getStatus().aberto()).count();
        log.info("Dados de demonstração carregados: {} fazendas, {} talhões, {} sensores, {} leituras, {} alertas abertos. "
                        + "Entre com {} / {} (agricultora) ou {} (outro agricultor).",
                fazendas.size(), talhoes, sensores, leituras, abertos, EMAIL_ANA, "<DEMO_SENHA>", EMAIL_BRUNO);
        return new Resumo(true, fazendas.size(), talhoes, sensores, leituras, abertos, previsao);
    }

    // ---- cadastros ----

    private record Hoje(LocalDate data, LocalDateTime agora) {
    }

    private Hoje hoje() {
        LocalDateTime agora = LocalDateTime.now(clock).withNano(0);
        return new Hoje(agora.toLocalDate(), agora);
    }

    private CulturaResponse cultura(String nome, int ciclo, double minima, double ideal, int raizCm) {
        return culturaService.criar(new CulturaRequest(nome, ciclo, minima, ideal, raizCm));
    }

    private FazendaResponse fazenda(Long usuarioId, String nome, String local, double area, double lat, double lon,
                                    String cidade, String uf) {
        return fazendaService.criar(usuarioId, new FazendaRequest(nome, local, area, lat, lon, cidade, uf));
    }

    /** Talhões retangulares vizinhos, em grade com {@code colunas} colunas a partir do ponto da fazenda. */
    private List<TalhaoResponse> talhoes(FazendaResponse fazenda, int quantidade, int colunas) {
        double dLat = 0.0060;
        double dLon = 0.0065;
        double lat0 = fazenda.latitude() - dLat;
        double lon0 = fazenda.longitude() - dLon * colunas / 2.0;
        double area = Math.round(dLat * METROS_POR_GRAU * dLon * METROS_POR_GRAU
                * Math.cos(Math.toRadians(fazenda.latitude())) / 10_000.0 * 10) / 10.0;
        List<TalhaoResponse> criados = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            double lat = lat0 + (i / colunas) * dLat;
            double lon = lon0 + (i % colunas) * dLon;
            criados.add(talhaoService.criar(fazenda.id(), new TalhaoRequest(
                    String.format(Locale.ROOT, "Talhão %02d", i + 1), area, retangulo(lon, lat, dLon, dLat))));
        }
        return criados;
    }

    private static JsonNode retangulo(double lon, double lat, double dLon, double dLat) {
        String json = String.format(Locale.ROOT,
                "{\"type\":\"Polygon\",\"coordinates\":[[[%.6f,%.6f],[%.6f,%.6f],[%.6f,%.6f],[%.6f,%.6f],[%.6f,%.6f]]]}",
                lon, lat, lon + dLon, lat, lon + dLon, lat + dLat, lon, lat + dLat, lon, lat);
        return JSON.readTree(json);
    }

    private static String safra(LocalDate plantio) {
        int inicio = plantio.getMonthValue() >= 7 ? plantio.getYear() : plantio.getYear() - 1;
        return inicio + "/" + (inicio + 1);
    }

    private PlantioResponse planejado(FazendaResponse f, TalhaoResponse t, CulturaResponse c, LocalDate data) {
        return plantioService.criar(f.id(), t.id(), new PlantioRequest(c.id(), "Plantio direto", safra(data), data, null));
    }

    private PlantioResponse emCultivo(FazendaResponse f, TalhaoResponse t, CulturaResponse c, LocalDate data) {
        PlantioResponse plantio = planejado(f, t, c, data);
        return plantioService.alterarStatus(f.id(), t.id(), plantio.id(),
                new AlterarStatusPlantioRequest(StatusPlantio.EM_CULTIVO, null, null));
    }

    /** Plantio de uma safra passada, já colhido, com produtividade em toneladas por hectare. */
    private void colhido(FazendaResponse f, TalhaoResponse t, CulturaResponse c, LocalDate plantio, LocalDate colheita,
                         double toneladasPorHectare) {
        PlantioResponse p = emCultivo(f, t, c, plantio);
        plantioService.alterarStatus(f.id(), t.id(), p.id(), new AlterarStatusPlantioRequest(
                StatusPlantio.COLHIDO, colheita, Math.round(toneladasPorHectare * t.areaHectares() * 1000.0)
                * 1.0));
    }

    // ---- sensores e leituras ----

    /** Cria solo, temperatura e umidade do ar no talhão e grava 72 h de leituras; devolve quantas. */
    private int sensores(FazendaResponse fazenda, String prefixo, TalhaoResponse talhao, Situacao situacao) {
        int total = 0;
        String sufixo = talhao.identificacao().replaceAll("\\D", "");
        for (VariavelMonitorada variavel : List.of(VariavelMonitorada.UMIDADE_SOLO, VariavelMonitorada.TEMPERATURA_AR,
                VariavelMonitorada.UMIDADE_AR)) {
            String codigo = prefixo + "-T" + sufixo + "-" + switch (variavel) {
                case UMIDADE_SOLO -> "SOLO";
                case TEMPERATURA_AR -> "TEMP";
                default -> "UMAR";
            };
            SensorResponse criado = sensorService.criar(fazenda.id(), talhao.id(),
                    new SensorRequest(codigo, variavel, hoje().data.minusDays(60)));
            // com o dispositivo já carregado (o runner não tem sessão aberta)
            Sensor sensor = sensorRepository.buscarNoTalhao(criado.id(), talhao.id()).orElseThrow();
            total += gravarLeituras(sensor, variavel, situacao);
        }
        return total;
    }

    private int gravarLeituras(Sensor sensor, VariavelMonitorada variavel, Situacao situacao) {
        Instant agora = clock.instant();
        List<LeituraService.Medicao> medicoes = new ArrayList<>();
        List<GeradorLeituraSimulada.Ponto> pontos = new ArrayList<>(gerador.gerarHistorico(variavel, agora, HISTORICO, PASSO));
        pontos.add(new GeradorLeituraSimulada.Ponto(agora.minus(Duration.ofSeconds(30)), gerador.gerar(variavel, agora)));
        Instant inicioCenario = agora.minus(JANELA_CENARIO);
        int indice = 0;
        for (GeradorLeituraSimulada.Ponto ponto : pontos) {
            double valor = ponto.valor();
            boolean noCenario = ponto.instante().isAfter(inicioCenario);
            if (situacao == Situacao.SENSOR_PARADO && ponto.instante().isAfter(agora.minus(PARADO_HA))) {
                continue;   // o sensor "para de enviar" há mais de 3 h: o alerta de falha nasce e a janela de umidade fica vazia
            }
            if (noCenario && variavel == VariavelMonitorada.UMIDADE_SOLO) {
                // valores estáveis e baixos: média da janela de 3 h abaixo do limiar da regra
                if (situacao == Situacao.SECA_ALTA) {
                    valor = 16.0 + (indice++ % 5) * 0.6;
                } else if (situacao == Situacao.SECA_BAIXA) {
                    valor = 27.0 + (indice++ % 5) * 0.5;
                }
            }
            medicoes.add(new LeituraService.Medicao(LocalDateTime.ofInstant(ponto.instante(), ZoneOffset.UTC), valor));
        }
        return leituraService.registrarLote(sensor, medicoes, OrigemLeitura.SIMULADA);
    }

    // ---- dados externos e alertas ----

    private boolean previsaoEEstacao(Long fazendaId) {
        boolean previsao = false;
        try {
            previsaoService.atualizarManualmente(fazendaId);
            previsao = true;
        } catch (RuntimeException erro) {
            log.warn("Demonstração: previsão da fazenda {} indisponível ({}).", fazendaId, erro.getMessage());
        }
        try {
            estacaoService.criar(fazendaId);
        } catch (RuntimeException erro) {
            log.warn("Demonstração: estação da fazenda {} indisponível ({}).", fazendaId, erro.getMessage());
        }
        return previsao;
    }

    /** Deixa um alerta "em atendimento" para mostrar o fluxo (os demais seguem abertos). */
    private void atenderUmAlerta(Long fazendaId, Long usuarioId) {
        try {
            alertaRepository.buscarDaFazendaPorStatus(fazendaId, List.of(StatusAlerta.ABERTO)).stream()
                    .filter(a -> a.getEvento() == EventoAlerta.SECA)
                    .findFirst()
                    .ifPresent(a -> alertaService.alterarStatus(fazendaId, a.getId(), usuarioId,
                            new AlterarStatusAlertaRequest(StatusAlerta.EM_ATENDIMENTO, "Equipe de irrigação acionada")));
        } catch (RuntimeException erro) {
            log.warn("Demonstração: não foi possível assumir um alerta: {}", erro.getMessage());
        }
    }
}
