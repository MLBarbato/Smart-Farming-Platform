package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusAlertaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.AlertaResponse;
import br.com.agricoladigital.smart_farming_api.dto.response.PaginaResponse;
import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.AlertaRepository;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
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
import java.util.Collection;

/**
 * Consulta e atendimento de alertas. A autorização por fazenda é feita no controller
 * ({@code @PreAuthorize}); aqui todo alerta é buscado sempre dentro da fazenda informada.
 */
@Service
public class AlertaService {
    static final int TAMANHO_PADRAO = 20;
    static final int TAMANHO_MAXIMO = 100;

    private final AlertaRepository alertaRepository;
    private final FazendaRepository fazendaRepository;
    private final TalhaoRepository talhaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    @Autowired
    public AlertaService(
            AlertaRepository alertaRepository,
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            UsuarioRepository usuarioRepository
    ) {
        this(alertaRepository, fazendaRepository, talhaoRepository, usuarioRepository, Clock.systemUTC());
    }

    AlertaService(
            AlertaRepository alertaRepository,
            FazendaRepository fazendaRepository,
            TalhaoRepository talhaoRepository,
            UsuarioRepository usuarioRepository,
            Clock clock
    ) {
        this.alertaRepository = alertaRepository;
        this.fazendaRepository = fazendaRepository;
        this.talhaoRepository = talhaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AlertaResponse> listar(
            Long fazendaId,
            Collection<StatusAlerta> status,
            EventoAlerta evento,
            CriticidadeAlerta criticidade,
            Long talhaoId,
            OffsetDateTime de,
            OffsetDateTime ate,
            Integer pagina,
            Integer tamanho
    ) {
        exigirFazenda(fazendaId);
        validar(de, ate, pagina, tamanho);
        if (talhaoId != null && talhaoRepository.findByIdAndFazenda_Id(talhaoId, fazendaId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Talhão não encontrado nesta fazenda.");
        }
        Specification<Alerta> base = (root, query, cb) -> cb.equal(root.get("talhao").get("fazenda").get("id"), fazendaId);
        return pesquisar(base, status, evento, criticidade, talhaoId, de, ate, pagina, tamanho);
    }

    /**
     * Alertas de todas as fazendas do usuário (de todas, para o administrador), com os mesmos filtros da
     * listagem por fazenda mais {@code fazendaId}. Fazenda de outro usuário se comporta como inexistente.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<AlertaResponse> listarDoUsuario(
            Long usuarioId,
            boolean administrador,
            Long fazendaId,
            Collection<StatusAlerta> status,
            EventoAlerta evento,
            CriticidadeAlerta criticidade,
            Long talhaoId,
            OffsetDateTime de,
            OffsetDateTime ate,
            Integer pagina,
            Integer tamanho
    ) {
        validar(de, ate, pagina, tamanho);
        if (fazendaId != null) {
            boolean acessivel = administrador ? fazendaRepository.existsById(fazendaId)
                    : fazendaRepository.existsByIdAndUsuario_Id(fazendaId, usuarioId);
            if (!acessivel) {
                throw new RecursoNaoEncontradoException("Fazenda não encontrada.");
            }
        }
        Specification<Alerta> base = (root, query, cb) -> cb.conjunction();
        if (!administrador) {
            base = base.and((root, query, cb) ->
                    cb.equal(root.get("talhao").get("fazenda").get("usuario").get("id"), usuarioId));
        }
        if (fazendaId != null) {
            base = base.and((root, query, cb) -> cb.equal(root.get("talhao").get("fazenda").get("id"), fazendaId));
        }
        return pesquisar(base, status, evento, criticidade, talhaoId, de, ate, pagina, tamanho);
    }

    private static void validar(OffsetDateTime de, OffsetDateTime ate, Integer pagina, Integer tamanho) {
        int numero = pagina == null ? 0 : pagina;
        int porPagina = tamanho == null ? TAMANHO_PADRAO : tamanho;
        if (numero < 0 || porPagina < 1 || porPagina > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException(
                    "pagina deve ser 0 ou mais e tamanho deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new RegraDeNegocioException("O início do período não pode ser posterior ao fim.");
        }
    }

    private PaginaResponse<AlertaResponse> pesquisar(
            Specification<Alerta> filtro,
            Collection<StatusAlerta> status,
            EventoAlerta evento,
            CriticidadeAlerta criticidade,
            Long talhaoId,
            OffsetDateTime de,
            OffsetDateTime ate,
            Integer pagina,
            Integer tamanho
    ) {
        if (status != null && !status.isEmpty()) {
            filtro = filtro.and((root, query, cb) -> root.get("status").in(status));
        }
        if (evento != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("evento"), evento));
        }
        if (criticidade != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("criticidade"), criticidade));
        }
        if (talhaoId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("talhao").get("id"), talhaoId));
        }
        if (de != null) {
            LocalDateTime inicio = utc(de);
            filtro = filtro.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDateTime>get("dataHora"), inicio));
        }
        if (ate != null) {
            LocalDateTime fim = utc(ate);
            filtro = filtro.and((root, query, cb) -> cb.lessThanOrEqualTo(root.<LocalDateTime>get("dataHora"), fim));
        }
        PageRequest pageable = PageRequest.of(pagina == null ? 0 : pagina, tamanho == null ? TAMANHO_PADRAO : tamanho,
                Sort.by(Sort.Direction.DESC, "dataHora").and(Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResponse.de(alertaRepository.findAll(filtro, pageable).map(AlertaService::paraResponse));
    }

    @Transactional(readOnly = true)
    public AlertaResponse buscar(Long fazendaId, Long alertaId) {
        return paraResponse(encontrar(fazendaId, alertaId));
    }

    /**
     * Muda o estado do alerta: ABERTO para EM_ATENDIMENTO, RESOLVIDO ou DISPENSADO; EM_ATENDIMENTO para
     * RESOLVIDO ou DISPENSADO. Qualquer outra transição é um conflito (alertas fechados não reabrem).
     * Dispensar exige nota. Se o job de avaliação alterar o mesmo alerta ao mesmo tempo, a gravação
     * falha com conflito (409) e basta repetir.
     */
    @Transactional
    public AlertaResponse alterarStatus(Long fazendaId, Long alertaId, Long usuarioId, AlterarStatusAlertaRequest request) {
        Alerta alerta = encontrar(fazendaId, alertaId);
        StatusAlerta destino = request.status();
        if (!alerta.getStatus().proximos().contains(destino)) {
            throw new ConflitoException("Um alerta " + alerta.getStatus() + " não pode passar para " + destino + ".");
        }
        if (destino == StatusAlerta.DISPENSADO && (request.nota() == null || request.nota().isBlank())) {
            throw new RegraDeNegocioException("Informe uma nota explicando por que o alerta foi dispensado.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        LocalDateTime agora = LocalDateTime.now(clock).withNano(0);
        switch (destino) {
            case EM_ATENDIMENTO -> alerta.assumir(usuario, agora, request.nota());
            case RESOLVIDO -> alerta.resolverManualmente(usuario, agora, request.nota());
            case DISPENSADO -> alerta.dispensar(usuario, agora, request.nota());
            default -> throw new IllegalStateException("Transição inesperada para " + destino);
        }
        return paraResponse(alerta);
    }

    private Alerta encontrar(Long fazendaId, Long alertaId) {
        return alertaRepository.findByIdAndTalhao_Fazenda_Id(alertaId, fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alerta não encontrado nesta fazenda."));
    }

    private void exigirFazenda(Long fazendaId) {
        if (!fazendaRepository.existsById(fazendaId)) {
            throw new RecursoNaoEncontradoException("Fazenda não encontrada.");
        }
    }

    private static LocalDateTime utc(OffsetDateTime instante) {
        return instante.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static OffsetDateTime utc(LocalDateTime instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    static AlertaResponse paraResponse(Alerta a) {
        RegraAlerta regra = a.getRegra();
        Leitura leitura = a.getLeitura();
        Usuario atendente = a.getAtendente();
        return new AlertaResponse(
                a.getId(),
                a.getTalhao().getId(),
                a.getTalhao().getFazenda().getId(),
                a.getTalhao().getFazenda().getNome(),
                a.getTalhao().getIdentificacao(),
                a.getEvento(),
                a.getCriticidade(),
                a.getStatus(),
                a.getMensagem(),
                utc(a.getDataHora()),
                utc(a.getAtualizadoEm()),
                new AlertaResponse.Regra(regra.getId(), regra.getOperador(), regra.getLimiar(), regra.getUnidade(),
                        regra.getJanelaHoras()),
                leitura == null ? null : new AlertaResponse.LeituraOrigem(leitura.getId(),
                        leitura.getDispositivo().getCodigoIdentificacao(), leitura.getValor(), utc(leitura.getDataHora())),
                atendente == null ? null : new AlertaResponse.Atendente(atendente.getId(), atendente.getNome()),
                utc(a.getAtendidoEm()),
                utc(a.getResolvidoEm()),
                a.getResolucao(),
                a.getNota(),
                utc(a.getNormalizadoEm())
        );
    }
}
