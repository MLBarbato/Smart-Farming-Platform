package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.client.ClimaClient;
import br.com.agricoladigital.smart_farming_api.client.DiaPrevisto;
import br.com.agricoladigital.smart_farming_api.dto.response.PrevisaoFazendaResponse;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.PrevisaoTempo;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoPrevisao;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PrevisaoTempoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/**
 * Consulta e atualização da previsão por fazenda. A chamada ao provedor acontece fora de qualquer
 * transação; se ela falhar, a previsão anterior é mantida (e passa a {@code DESATUALIZADA} quando
 * vence a validade). Dias já passados nunca são devolvidos.
 */
@Service
public class PrevisaoService {
    public static final String ATRIBUICAO =
            "Dados meteorológicos por Open-Meteo.com (https://open-meteo.com), licença CC BY 4.0.";

    private final FazendaRepository fazendaRepository;
    private final PrevisaoTempoRepository previsaoRepository;
    private final PrevisaoArmazenamento armazenamento;
    private final ClimaClient clima;
    private final int dias;
    private final Duration validade;
    private final Duration intervaloMinimoManual;
    private final Clock clock;

    @Autowired
    public PrevisaoService(
            FazendaRepository fazendaRepository,
            PrevisaoTempoRepository previsaoRepository,
            PrevisaoArmazenamento armazenamento,
            ClimaClient clima,
            @Value("${app.previsao.dias:7}") int dias,
            @Value("${app.previsao.validade-horas:12}") long validadeHoras,
            @Value("${app.previsao.intervalo-minimo-manual-minutos:5}") long intervaloMinimoManualMinutos
    ) {
        this(fazendaRepository, previsaoRepository, armazenamento, clima, dias, validadeHoras,
                intervaloMinimoManualMinutos, Clock.systemUTC());
    }

    PrevisaoService(
            FazendaRepository fazendaRepository,
            PrevisaoTempoRepository previsaoRepository,
            PrevisaoArmazenamento armazenamento,
            ClimaClient clima,
            int dias,
            long validadeHoras,
            long intervaloMinimoManualMinutos,
            Clock clock
    ) {
        if (dias < 1 || dias > 16 || validadeHoras < 1 || intervaloMinimoManualMinutos < 0) {
            throw new IllegalArgumentException(
                    "app.previsao.*: dias entre 1 e 16, validade-horas positiva e intervalo mínimo não negativo."
            );
        }
        this.fazendaRepository = fazendaRepository;
        this.previsaoRepository = previsaoRepository;
        this.armazenamento = armazenamento;
        this.clima = clima;
        this.dias = dias;
        this.validade = Duration.ofHours(validadeHoras);
        this.intervaloMinimoManual = Duration.ofMinutes(intervaloMinimoManualMinutos);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PrevisaoFazendaResponse consultar(Long fazendaId) {
        return montar(encontrarFazenda(fazendaId));
    }

    /**
     * Força uma atualização. Se a última consulta foi há menos que o intervalo mínimo, devolve a
     * previsão já gravada sem chamar o provedor (protege o limite de uso da API).
     */
    public PrevisaoFazendaResponse atualizarManualmente(Long fazendaId) {
        Fazenda fazenda = encontrarFazenda(fazendaId);
        if (!temCoordenadas(fazenda)) {
            throw new RegraDeNegocioException(
                    "Cadastre a latitude e a longitude da fazenda para obter a previsão."
            );
        }
        LocalDateTime ultima = ultimaConsulta(fazendaId);
        if (ultima != null && Duration.between(ultima, agoraUtc()).compareTo(intervaloMinimoManual) < 0) {
            return consultar(fazendaId);
        }
        atualizar(fazenda);
        return consultar(fazendaId);
    }

    /** Consulta o provedor e substitui a previsão da fazenda; propaga falhas do provedor. */
    public void atualizar(Fazenda fazenda) {
        List<DiaPrevisto> previsao = clima.buscarPrevisaoDiaria(fazenda.getLatitude(), fazenda.getLongitude(), dias);
        armazenamento.substituir(fazenda.getId(), previsao, clima.fonte(), agoraUtc());
    }

    private PrevisaoFazendaResponse montar(Fazenda fazenda) {
        int validadeHoras = (int) validade.toHours();
        if (!temCoordenadas(fazenda)) {
            return new PrevisaoFazendaResponse(fazenda.getId(), SituacaoPrevisao.SEM_COORDENADAS, null,
                    ATRIBUICAO, null, validadeHoras, List.of());
        }
        LocalDate hoje = agoraUtc().toLocalDate().minusDays(1);
        List<PrevisaoTempo> gravadas = previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(fazenda.getId())
                .stream().filter(p -> !p.getDataPrevisao().isBefore(hoje)).toList();
        if (gravadas.isEmpty()) {
            return new PrevisaoFazendaResponse(fazenda.getId(), SituacaoPrevisao.SEM_DADOS, null, ATRIBUICAO,
                    null, validadeHoras, List.of());
        }
        LocalDateTime consultadaEm = gravadas.stream().map(PrevisaoTempo::getConsultadaEm)
                .max(Comparator.naturalOrder()).orElseThrow();
        SituacaoPrevisao situacao = Duration.between(consultadaEm, agoraUtc()).compareTo(validade) > 0
                ? SituacaoPrevisao.DESATUALIZADA : SituacaoPrevisao.ATUAL;
        return new PrevisaoFazendaResponse(
                fazenda.getId(), situacao, gravadas.get(0).getFonteApi(), ATRIBUICAO,
                consultadaEm.atOffset(ZoneOffset.UTC), validadeHoras,
                gravadas.stream().map(p -> new PrevisaoFazendaResponse.Dia(
                        p.getDataPrevisao(), p.getTemperaturaPrevista(), p.getTemperaturaMinima(),
                        p.getTemperaturaMaxima(), p.getPrecipitacaoPrevista(), p.getProbabilidadePrecipitacao()
                )).toList()
        );
    }

    private LocalDateTime ultimaConsulta(Long fazendaId) {
        return previsaoRepository.findAllByFazenda_IdOrderByDataPrevisao(fazendaId).stream()
                .map(PrevisaoTempo::getConsultadaEm).max(Comparator.naturalOrder()).orElse(null);
    }

    private Fazenda encontrarFazenda(Long fazendaId) {
        return fazendaRepository.findById(fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fazenda não encontrada: " + fazendaId));
    }

    private static boolean temCoordenadas(Fazenda fazenda) {
        return fazenda.getLatitude() != null && fazenda.getLongitude() != null;
    }

    private LocalDateTime agoraUtc() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
