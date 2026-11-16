package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.response.PaginaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.RecomendacaoIrrigacaoResponse;
import br.com.agricoladigital.smart_farming_api.entity.RecomendacaoIrrigacao;
import br.com.agricoladigital.smart_farming_api.entity.SituacaoIrrigacao;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import br.com.agricoladigital.smart_farming_api.repository.RecomendacaoIrrigacaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consulta de recomendações de irrigação. A autorização por fazenda é feita no controller
 * ({@code @PreAuthorize}); aqui tudo é buscado dentro da fazenda informada.
 */
@Service
public class RecomendacaoIrrigacaoService {
    static final int TAMANHO_PADRAO = 20;
    static final int TAMANHO_MAXIMO = 100;

    private final RecomendacaoIrrigacaoRepository recomendacaoRepository;
    private final RecomendadorIrrigacao recomendador;
    private final FazendaRepository fazendaRepository;
    private final TalhaoRepository talhaoRepository;
    private final PlantioRepository plantioRepository;
    private final Clock clock;

    @Autowired
    public RecomendacaoIrrigacaoService(
            RecomendacaoIrrigacaoRepository recomendacaoRepository,
            RecomendadorIrrigacao recomendador,
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository
    ) {
        this(recomendacaoRepository, recomendador, fazendaRepository, talhaoRepository, plantioRepository,
                Clock.systemUTC());
    }

    RecomendacaoIrrigacaoService(
            RecomendacaoIrrigacaoRepository recomendacaoRepository,
            RecomendadorIrrigacao recomendador,
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            PlantioRepository plantioRepository,
            Clock clock
    ) {
        this.recomendacaoRepository = recomendacaoRepository;
        this.recomendador = recomendador;
        this.fazendaRepository = fazendaRepository;
        this.talhaoRepository = talhaoRepository;
        this.plantioRepository = plantioRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<RecomendacaoIrrigacaoResponse> listar(
            Long fazendaId,
            Long talhaoId,
            SituacaoIrrigacao situacao,
            OffsetDateTime de,
            OffsetDateTime ate,
            Integer pagina,
            Integer tamanho
    ) {
        exigirFazenda(fazendaId);
        int numero = pagina == null ? 0 : pagina;
        int porPagina = tamanho == null ? TAMANHO_PADRAO : tamanho;
        if (numero < 0 || porPagina < 1 || porPagina > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException(
                    "pagina deve ser 0 ou mais e tamanho deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new RegraDeNegocioException("O início do período não pode ser posterior ao fim.");
        }
        if (talhaoId != null && talhaoRepository.findByIdAndFazenda_Id(talhaoId, fazendaId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Talhão não encontrado nesta fazenda.");
        }
        Specification<RecomendacaoIrrigacao> filtro =
                (root, query, cb) -> cb.equal(root.get("talhao").get("fazenda").get("id"), fazendaId);
        if (talhaoId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("talhao").get("id"), talhaoId));
        }
        if (situacao != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("situacao"), situacao));
        }
        if (de != null) {
            LocalDateTime inicio = utc(de);
            filtro = filtro.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDateTime>get("dataHora"), inicio));
        }
        if (ate != null) {
            LocalDateTime fim = utc(ate);
            filtro = filtro.and((root, query, cb) -> cb.lessThanOrEqualTo(root.<LocalDateTime>get("dataHora"), fim));
        }
        PageRequest pageable = PageRequest.of(numero, porPagina,
                Sort.by(Sort.Direction.DESC, "dataHora").and(Sort.by(Sort.Direction.DESC, "id")));
        LocalDateTime agora = agora();
        return PaginaResponse.de(recomendacaoRepository.findAll(filtro, pageable).map(r -> paraResponse(r, agora)));
    }

    /** A recomendação mais recente de cada talhão que está com plantio em cultivo. */
    @Transactional(readOnly = true)
    public List<RecomendacaoIrrigacaoResponse> atuais(Long fazendaId) {
        exigirFazenda(fazendaId);
        return atuaisDaFazenda(fazendaId);
    }

    @Transactional(readOnly = true)
    public RecomendacaoIrrigacaoResponse buscar(Long fazendaId, Long recomendacaoId) {
        RecomendacaoIrrigacao recomendacao = recomendacaoRepository.findByIdAndTalhao_Fazenda_Id(recomendacaoId, fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recomendação não encontrada nesta fazenda."));
        return paraResponse(recomendacao, agora());
    }

    /** Recalcula agora (cálculo local, sem chamadas externas) e devolve as recomendações atuais. */
    public List<RecomendacaoIrrigacaoResponse> calcular(Long fazendaId) {
        exigirFazenda(fazendaId);
        recomendador.calcularFazenda(fazendaId);
        return atuaisDaFazenda(fazendaId);
    }

    private List<RecomendacaoIrrigacaoResponse> atuaisDaFazenda(Long fazendaId) {
        Set<Long> emCultivo = plantioRepository.buscarPorFazendaEStatus(fazendaId, StatusPlantio.EM_CULTIVO).stream()
                .map(p -> p.getTalhao().getId()).collect(Collectors.toSet());
        LocalDateTime agora = agora();
        return recomendacaoRepository.ultimasDaFazenda(fazendaId).stream()
                .filter(r -> emCultivo.contains(r.getTalhao().getId()))
                .sorted(Comparator.comparing((RecomendacaoIrrigacao r) -> r.getTalhao().getIdentificacao())
                        .thenComparing(RecomendacaoIrrigacao::getId))
                .map(r -> paraResponse(r, agora))
                .toList();
    }

    private void exigirFazenda(Long fazendaId) {
        if (!fazendaRepository.existsById(fazendaId)) {
            throw new RecursoNaoEncontradoException("Fazenda não encontrada.");
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private static LocalDateTime utc(OffsetDateTime instante) {
        return instante.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static OffsetDateTime utc(LocalDateTime instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    static RecomendacaoIrrigacaoResponse paraResponse(RecomendacaoIrrigacao r, LocalDateTime agora) {
        boolean temCultura = r.getCulturaNome() != null;
        boolean usouPrevisao = r.getPrevisaoConsultadaEm() != null && r.getChuvaPrevistaMm() != null;
        return new RecomendacaoIrrigacaoResponse(
                r.getId(),
                r.getTalhao().getId(),
                r.getTalhao().getIdentificacao(),
                r.getSituacao(),
                r.getVolumeRecomendadoMm(),
                r.getJustificativa(),
                utc(r.getDataHora()),
                utc(r.getValidoAte()),
                !agora.isBefore(r.getValidoAte()),
                temCultura ? new RecomendacaoIrrigacaoResponse.Cultura(r.getCulturaNome(), r.getUmidadeMinima(),
                        r.getUmidadeIdeal(), r.getProfundidadeRaizCm()) : null,
                new RecomendacaoIrrigacaoResponse.Umidade(r.getUmidadeAtual(), r.getOrigemUmidade()),
                r.getDeficitMm(),
                new RecomendacaoIrrigacaoResponse.Chuva(r.getChuvaPrevistaMm(), r.getChuvaEfetivaMm()),
                r.getTemperaturaMaximaPrevista(),
                r.isAjusteTemperatura(),
                usouPrevisao ? new RecomendacaoIrrigacaoResponse.Previsao(r.getPrevisaoFonte(),
                        utc(r.getPrevisaoConsultadaEm()), PrevisaoService.ATRIBUICAO) : null,
                r.listaDadosFaltantes()
        );
    }
}
