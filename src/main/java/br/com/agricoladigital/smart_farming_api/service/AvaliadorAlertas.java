package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.*;
import br.com.agricoladigital.smart_farming_api.service.ConciliadorAlertas.Acao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Avalia as regras de alerta de uma fazenda sobre os dados já gravados (leituras, estação e previsão)
 * e concilia o resultado com os alertas existentes. Só lê o banco: nenhuma chamada externa.
 *
 * <p>Escopo: SECA, EXCESSO_PRECIPITACAO e RISCO_GEADA valem para talhões com plantio EM_CULTIVO
 * (nos demais, a condição é tratada como normalizada); FALHA_SENSOR vale para os sensores ativos
 * de qualquer talhão, e a falha da estação conta para os talhões em cultivo. Sem dado para avaliar,
 * nada é criado nem normalizado. Previsão vencida não dispara regra. A previsão é diária, em datas
 * locais da fazenda; aproximamos "hoje" pela data UTC.
 *
 * <p>Uma colisão com o índice único de alerta aberto (duas avaliações simultâneas) desfaz o ciclo da
 * fazenda, que é refeito na execução seguinte.
 */
@Slf4j
@Service
public class AvaliadorAlertas {
    static final int HORAS_TEMPERATURA_ESTACAO = 3;
    private static final int MAX_MENSAGEM = 500;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final FazendaRepository fazendaRepository;
    private final TalhaoRepository talhaoRepository;
    private final PlantioRepository plantioRepository;
    private final SensorRepository sensorRepository;
    private final EstacaoMeteorologicaRepository estacaoRepository;
    private final PrevisaoTempoRepository previsaoRepository;
    private final LeituraRepository leituraRepository;
    private final RegraAlertaRepository regraRepository;
    private final AlertaRepository alertaRepository;
    private final ConciliadorAlertas conciliador;
    private final Duration validadePrevisao;
    private final Duration reabertura;
    private final Clock clock;

    @Autowired
    public AvaliadorAlertas(
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            PrevisaoTempoRepository previsaoRepository,
            LeituraRepository leituraRepository,
            RegraAlertaRepository regraRepository,
            AlertaRepository alertaRepository,
            ConciliadorAlertas conciliador,
            @Value("${app.previsao.validade-horas:12}") long validadePrevisaoHoras,
            @Value("${app.alertas.reabertura-horas:6}") long reaberturaHoras
    ) {
        this(fazendaRepository, talhaoRepository, plantioRepository, sensorRepository, estacaoRepository,
                previsaoRepository, leituraRepository, regraRepository, alertaRepository, conciliador,
                validadePrevisaoHoras, reaberturaHoras, Clock.systemUTC());
    }

    AvaliadorAlertas(
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository,
            SensorRepository sensorRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            PrevisaoTempoRepository previsaoRepository,
            LeituraRepository leituraRepository,
            RegraAlertaRepository regraRepository,
            AlertaRepository alertaRepository,
            ConciliadorAlertas conciliador,
            long validadePrevisaoHoras,
            long reaberturaHoras,
            Clock clock
    ) {
        if (validadePrevisaoHoras < 1 || reaberturaHoras < 0) {
            throw new IllegalArgumentException(
                    "app.previsao.validade-horas deve ser positiva e app.alertas.reabertura-horas não negativa.");
        }
        this.fazendaRepository = fazendaRepository;
        this.talhaoRepository = talhaoRepository;
        this.plantioRepository = plantioRepository;
        this.sensorRepository = sensorRepository;
        this.estacaoRepository = estacaoRepository;
        this.previsaoRepository = previsaoRepository;
        this.leituraRepository = leituraRepository;
        this.regraRepository = regraRepository;
        this.alertaRepository = alertaRepository;
        this.conciliador = conciliador;
        this.validadePrevisao = Duration.ofHours(validadePrevisaoHoras);
        this.reabertura = Duration.ofHours(reaberturaHoras);
        this.clock = clock;
    }

    private record ChaveAlerta(Long talhaoId, EventoAlerta evento) {}

    private record Medicao(double valor, Leitura leitura, String descricao) {}

    /** Dados carregados uma vez por avaliação da fazenda. */
    private static final class Contexto {
        LocalDateTime agora;
        Set<Long> emCultivo;
        List<Sensor> sensoresTalhao;
        EstacaoMeteorologica estacao;
        List<Sensor> sensoresEstacao = List.of();
        List<PrevisaoTempo> previsao = List.of();
        boolean previsaoAtual;
        Map<EventoAlerta, List<RegraAlerta>> regras;
        Map<Long, Leitura> ultimas = Map.of();

        List<RegraAlerta> regras(EventoAlerta evento) {
            return regras.getOrDefault(evento, List.of());
        }
    }

    /** Avalia todos os eventos de todos os talhões da fazenda e devolve quantas vezes cada ação ocorreu. */
    @Transactional
    public Map<Acao, Integer> avaliarFazenda(Long fazendaId) {
        Map<Acao, Integer> contagem = new EnumMap<>(Acao.class);
        if (fazendaRepository.findById(fazendaId).isEmpty()) {
            return contagem;
        }
        List<Talhao> talhoes = talhaoRepository.findAllByFazenda_Id(fazendaId);
        if (talhoes.isEmpty()) {
            return contagem;
        }
        Contexto c = carregar(fazendaId);

        Map<ChaveAlerta, Alerta> abertos = alertaRepository
                .buscarDaFazendaPorStatus(fazendaId, EnumSet.of(StatusAlerta.ABERTO, StatusAlerta.EM_ATENDIMENTO))
                .stream().collect(Collectors.toMap(
                        a -> new ChaveAlerta(a.getTalhao().getId(), a.getEvento()), a -> a, (a, b) -> a));
        Map<ChaveAlerta, CriticidadeAlerta> fechados = new HashMap<>();
        alertaRepository.buscarFechadosDesde(fazendaId,
                EnumSet.of(StatusAlerta.RESOLVIDO, StatusAlerta.DISPENSADO), c.agora.minus(reabertura))
                .forEach(a -> fechados.merge(new ChaveAlerta(a.getTalhao().getId(), a.getEvento()),
                        a.getCriticidade(), (x, y) -> x.compareTo(y) >= 0 ? x : y));

        for (Talhao talhao : talhoes) {
            for (EventoAlerta evento : EventoAlerta.values()) {
                ResultadoAvaliacao resultado = avaliar(evento, talhao, c);
                ChaveAlerta chave = new ChaveAlerta(talhao.getId(), evento);
                Acao acao = conciliador.conciliar(
                        talhao, evento, resultado, abertos.get(chave), fechados.get(chave), c.agora);
                contagem.merge(acao, 1, Integer::sum);
            }
        }
        return contagem;
    }

    private Contexto carregar(Long fazendaId) {
        Contexto c = new Contexto();
        c.agora = LocalDateTime.now(clock).withNano(0);
        c.emCultivo = plantioRepository.buscarPorFazendaEStatus(fazendaId, StatusPlantio.EM_CULTIVO).stream()
                .map(p -> p.getTalhao().getId()).collect(Collectors.toSet());
        c.sensoresTalhao = sensorRepository.listarPorFazenda(fazendaId);
        c.estacao = estacaoRepository.buscarDaFazenda(fazendaId).orElse(null);
        if (c.estacao != null) {
            c.sensoresEstacao = sensorRepository.listarDaEstacao(c.estacao.getId());
        }
        c.previsao = previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(fazendaId);
        c.previsaoAtual = !c.previsao.isEmpty()
                && Duration.between(c.previsao.get(0).getConsultadaEm(), c.agora).compareTo(validadePrevisao) <= 0;
        if (!c.previsao.isEmpty() && !c.previsaoAtual) {
            log.info("Fazenda {}: previsão desatualizada; as regras baseadas em previsão foram ignoradas.", fazendaId);
        }
        c.regras = regraRepository.findAll().stream()
                .filter(RegraAlerta::getAtiva)
                .sorted(Comparator.comparing(RegraAlerta::getCriticidade).reversed())
                .collect(Collectors.groupingBy(RegraAlerta::getEvento, () -> new EnumMap<>(EventoAlerta.class),
                        Collectors.toList()));
        Set<Long> ids = new HashSet<>();
        c.sensoresTalhao.forEach(s -> ids.add(s.getId()));
        c.sensoresEstacao.forEach(s -> ids.add(s.getId()));
        if (!ids.isEmpty()) {
            c.ultimas = leituraRepository.ultimasPorDispositivos(ids).stream()
                    .collect(Collectors.toMap(l -> l.getDispositivo().getId(), l -> l, (a, b) -> a));
        }
        return c;
    }

    private ResultadoAvaliacao avaliar(EventoAlerta evento, Talhao talhao, Contexto c) {
        if (evento == EventoAlerta.FALHA_SENSOR) {
            return falhaSensor(talhao, c);
        }
        if (!c.emCultivo.contains(talhao.getId())) {
            return ResultadoAvaliacao.NORMAL;
        }
        return switch (evento) {
            case SECA -> seca(talhao, c);
            case EXCESSO_PRECIPITACAO -> excessoPrecipitacao(c);
            case RISCO_GEADA -> riscoGeada(c);
            case FALHA_SENSOR -> throw new IllegalStateException();
        };
    }

    // ---- SECA ---------------------------------------------------------------------------------

    private ResultadoAvaliacao seca(Talhao talhao, Contexto c) {
        List<Sensor> fonte = sensoresAtivos(c.sensoresTalhao.stream()
                .filter(s -> talhao.getId().equals(s.getDispositivo().getTalhao().getId())), VariavelMonitorada.UMIDADE_SOLO);
        String origem = "sensor do talhão";
        if (fonte.isEmpty()) {
            fonte = sensoresDaEstacao(c, VariavelMonitorada.UMIDADE_SOLO);
            origem = "estação da fazenda";
        }
        if (fonte.isEmpty()) {
            return ResultadoAvaliacao.SEM_DADOS;
        }
        List<Long> ids = fonte.stream().map(Sensor::getId).toList();
        String origemFinal = origem;
        return avaliarPorRegra(c.regras(EventoAlerta.SECA), regra -> {
            LocalDateTime de = c.agora.minusHours(regra.getJanelaHoras());
            Double media = leituraRepository.mediaNoIntervalo(ids, de, c.agora);
            if (media == null) {
                return Optional.empty();
            }
            Leitura ultima = leituraRepository
                    .findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(ids, de, c.agora).orElse(null);
            return Optional.of(new Medicao(media, ultima, "Seca: umidade média do solo de %s %% nas últimas %d h (%s)"
                    .formatted(num(media), regra.getJanelaHoras(), origemFinal)));
        });
    }

    // ---- EXCESSO_PRECIPITACAO -----------------------------------------------------------------

    private ResultadoAvaliacao excessoPrecipitacao(Contexto c) {
        List<Long> idsChuva = sensoresDaEstacao(c, VariavelMonitorada.PRECIPITACAO).stream().map(Sensor::getId).toList();
        Double previsto = c.previsaoAtual ? chuvaPrevista(c) : null;
        return avaliarPorRegra(c.regras(EventoAlerta.EXCESSO_PRECIPITACAO), regra -> {
            Medicao melhor = null;
            if (!idsChuva.isEmpty()) {
                LocalDateTime de = c.agora.minusHours(regra.getJanelaHoras());
                Double soma = leituraRepository.somaNoIntervalo(idsChuva, de, c.agora);
                if (soma != null) {
                    Leitura ultima = leituraRepository
                            .findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(idsChuva, de, c.agora)
                            .orElse(null);
                    melhor = new Medicao(soma, ultima, "Excesso de precipitação: %s mm acumulados nas últimas %d h (estação da fazenda)"
                            .formatted(num(soma), regra.getJanelaHoras()));
                }
            }
            if (previsto != null && (melhor == null || previsto > melhor.valor())) {
                melhor = new Medicao(previsto, null, "Excesso de precipitação (previsão): até %s mm previstos para hoje ou amanhã (%s)"
                        .formatted(num(previsto), fontePrevisao(c)));
            }
            return Optional.ofNullable(melhor);
        });
    }

    /** Maior precipitação diária prevista para hoje e amanhã (a previsão é diária; é uma aproximação de 24 h). */
    private Double chuvaPrevista(Contexto c) {
        LocalDate hoje = c.agora.toLocalDate();
        return c.previsao.stream()
                .filter(p -> !p.getDataPrevisao().isBefore(hoje) && !p.getDataPrevisao().isAfter(hoje.plusDays(1)))
                .map(PrevisaoTempo::getPrecipitacaoPrevista).filter(Objects::nonNull)
                .max(Double::compare).orElse(null);
    }

    // ---- RISCO_GEADA --------------------------------------------------------------------------

    private ResultadoAvaliacao riscoGeada(Contexto c) {
        List<RegraAlerta> regras = c.regras(EventoAlerta.RISCO_GEADA);
        if (regras.isEmpty()) {
            return ResultadoAvaliacao.SEM_DADOS;
        }
        // Estação já fria: usa o limiar da criticidade MEDIA (ou o da mais severa, se não houver) e dispara a mais severa.
        RegraAlerta referencia = regras.stream().filter(r -> r.getCriticidade() == CriticidadeAlerta.MEDIA)
                .findFirst().orElse(regras.get(0));
        List<Long> idsTemperatura = sensoresDaEstacao(c, VariavelMonitorada.TEMPERATURA_AR).stream().map(Sensor::getId).toList();
        Leitura atual = idsTemperatura.isEmpty() ? null : leituraRepository
                .findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(
                        idsTemperatura, c.agora.minusHours(HORAS_TEMPERATURA_ESTACAO), c.agora).orElse(null);
        boolean haDados = atual != null;
        for (RegraAlerta regra : regras) {
            Double minima = c.previsaoAtual ? minimaPrevista(c, regra) : null;
            if (minima != null) {
                haDados = true;
                if (regra.getOperador().satisfeito(minima, regra.getLimiar())) {
                    return ResultadoAvaliacao.condicao(regra, mensagem(regra,
                            "Risco de geada (previsão): temperatura mínima prevista de %s °C nos próximos %d dias (%s)"
                                    .formatted(num(minima), diasDaJanela(regra), fontePrevisao(c)), null), null);
                }
            }
            if (atual != null && regra == regras.get(0)
                    && referencia.getOperador().satisfeito(atual.getValor(), referencia.getLimiar())) {
                return ResultadoAvaliacao.condicao(regra, mensagem(regra,
                        "Risco de geada: temperatura atual de %s °C na estação da fazenda, já no limite de %s %s °C"
                                .formatted(num(atual.getValor()), referencia.getOperador().simbolo(),
                                        num(referencia.getLimiar())), null), atual);
            }
        }
        return haDados ? ResultadoAvaliacao.NORMAL : ResultadoAvaliacao.SEM_DADOS;
    }

    private int diasDaJanela(RegraAlerta regra) {
        return Math.max(1, (regra.getJanelaHoras() + 23) / 24);
    }

    private Double minimaPrevista(Contexto c, RegraAlerta regra) {
        LocalDate hoje = c.agora.toLocalDate();
        LocalDate ate = hoje.plusDays(diasDaJanela(regra) - 1L);
        return c.previsao.stream()
                .filter(p -> !p.getDataPrevisao().isBefore(hoje) && !p.getDataPrevisao().isAfter(ate))
                .map(PrevisaoTempo::getTemperaturaMinima).filter(Objects::nonNull)
                .min(Double::compare).orElse(null);
    }

    // ---- FALHA_SENSOR -------------------------------------------------------------------------

    private record Falha(long minutos, Leitura ultima, String descricao) {}

    private ResultadoAvaliacao falhaSensor(Talhao talhao, Contexto c) {
        List<RegraAlerta> regras = c.regras(EventoAlerta.FALHA_SENSOR);
        if (regras.isEmpty()) {
            return ResultadoAvaliacao.SEM_DADOS;
        }
        List<Falha> sem = new ArrayList<>();
        int monitorados = 0;
        for (Sensor sensor : c.sensoresTalhao) {
            Dispositivo d = sensor.getDispositivo();
            if (!talhao.getId().equals(d.getTalhao().getId()) || d.getStatus() != StatusDispositivo.ATIVO) {
                continue;
            }
            Leitura ultima = c.ultimas.get(sensor.getId());
            LocalDateTime referencia = ultima != null ? ultima.getDataHora()
                    // Sem nenhuma leitura: dá-se a tolerância do dia da instalação; sem data, não há como avaliar.
                    : d.getDataInstalacao() != null ? d.getDataInstalacao().plusDays(1).atStartOfDay() : null;
            if (referencia == null || referencia.isAfter(c.agora)) {
                continue;
            }
            monitorados++;
            long minutos = Duration.between(referencia, c.agora).toMinutes();
            sem.add(new Falha(minutos, ultima, "sensor %s (%s)".formatted(d.getCodigoIdentificacao(),
                    sensor.getTipoSensor())));
        }
        if (c.estacao != null && c.emCultivo.contains(talhao.getId())
                && c.estacao.getUltimaSincronizacao() != null
                && c.estacao.getDispositivo().getStatus() == StatusDispositivo.ATIVO) {
            monitorados++;
            if (SituacaoEstacao.de(c.estacao, c.agora) == SituacaoEstacao.DESATUALIZADA) {
                sem.add(new Falha(Duration.between(c.estacao.getUltimaSincronizacao(), c.agora).toMinutes(),
                        null, "estação da fazenda"));
            }
        }
        if (monitorados == 0) {
            return ResultadoAvaliacao.NORMAL;
        }
        Falha pior = sem.stream().max(Comparator.comparingLong(Falha::minutos)).orElse(null);
        if (pior == null) {
            return ResultadoAvaliacao.NORMAL;
        }
        for (RegraAlerta regra : regras) {
            if (regra.getOperador().satisfeito(pior.minutos(), regra.getLimiar())) {
                long distintas = sem.stream().filter(f -> regra.getOperador().satisfeito(f.minutos(), regra.getLimiar())).count();
                String texto = "Falha de sensor: %s sem leitura há %s%s"
                        .formatted(pior.descricao(), duracao(pior.minutos()),
                                distintas > 1 ? " (e mais %d fonte(s) em falha)".formatted(distintas - 1) : "");
                return ResultadoAvaliacao.condicao(regra, mensagem(regra, texto, "min"), pior.ultima());
            }
        }
        return ResultadoAvaliacao.NORMAL;
    }

    // ---- auxiliares ---------------------------------------------------------------------------

    /** Percorre as regras da mais severa para a menos; a primeira satisfeita define o alerta. */
    private ResultadoAvaliacao avaliarPorRegra(List<RegraAlerta> regras, Function<RegraAlerta, Optional<Medicao>> medir) {
        boolean haDados = false;
        for (RegraAlerta regra : regras) {
            Optional<Medicao> medicao = medir.apply(regra);
            if (medicao.isEmpty()) {
                continue;
            }
            haDados = true;
            if (regra.getOperador().satisfeito(medicao.get().valor(), regra.getLimiar())) {
                return ResultadoAvaliacao.condicao(regra, mensagem(regra, medicao.get().descricao(), null),
                        medicao.get().leitura());
            }
        }
        return haDados ? ResultadoAvaliacao.NORMAL : ResultadoAvaliacao.SEM_DADOS;
    }

    private String mensagem(RegraAlerta regra, String descricao, String unidadeOverride) {
        String texto = "%s. Regra: %s %s %s (criticidade %s).".formatted(descricao, regra.getOperador().simbolo(),
                num(regra.getLimiar()), unidadeOverride != null ? unidadeOverride : regra.getUnidade(),
                regra.getCriticidade());
        return texto.length() <= MAX_MENSAGEM ? texto : texto.substring(0, MAX_MENSAGEM - 1) + "…";
    }

    private List<Sensor> sensoresAtivos(java.util.stream.Stream<Sensor> sensores, VariavelMonitorada variavel) {
        return sensores.filter(s -> s.getTipoSensor() == variavel
                && s.getDispositivo().getStatus() == StatusDispositivo.ATIVO).toList();
    }

    private List<Sensor> sensoresDaEstacao(Contexto c, VariavelMonitorada variavel) {
        if (c.estacao == null || c.estacao.getDispositivo().getStatus() != StatusDispositivo.ATIVO) {
            return List.of();
        }
        return sensoresAtivos(c.sensoresEstacao.stream(), variavel);
    }

    private String fontePrevisao(Contexto c) {
        String fonte = c.previsao.get(0).getFonteApi();
        return fonte == null || fonte.isBlank() ? "previsão do tempo" : fonte;
    }

    private static String duracao(long minutos) {
        return minutos >= 120 ? (minutos / 60) + " h" : minutos + " min";
    }

    private static String num(double valor) {
        String texto = String.format(PT_BR, "%.1f", valor);
        return texto.endsWith(",0") ? texto.substring(0, texto.length() - 2) : texto;
    }
}
