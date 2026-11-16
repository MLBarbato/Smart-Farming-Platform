package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusPlantioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.PlantioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.RegistrarColheitaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.PlantioResponse;
import br.com.agricoladigital.smart_farming_api.entity.Cultura;
import br.com.agricoladigital.smart_farming_api.entity.Plantio;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import br.com.agricoladigital.smart_farming_api.entity.Talhao;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.CulturaRepository;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PlantioService {
    /** Marca "sem data de fim" nas consultas de sobreposição (cabe em DATE do PostgreSQL). */
    private static final LocalDate SEM_FIM = LocalDate.of(9999, 12, 31);
    /** O ano-safra começa em julho: de jul/2026 a jun/2027 é a safra 2026/2027. */
    private static final int MES_INICIO_SAFRA = 7;
    private static final Long SEM_ID = -1L;

    private final PlantioRepository plantioRepository;
    private final TalhaoRepository talhaoRepository;
    private final CulturaRepository culturaRepository;
    private final FazendaRepository fazendaRepository;

    public PlantioService(
            PlantioRepository plantioRepository,
            TalhaoRepository talhaoRepository,
            CulturaRepository culturaRepository,
            FazendaRepository fazendaRepository
    ) {
        this.plantioRepository = plantioRepository;
        this.talhaoRepository = talhaoRepository;
        this.culturaRepository = culturaRepository;
        this.fazendaRepository = fazendaRepository;
    }

    @Transactional
    public PlantioResponse criar(Long fazendaId, Long talhaoId, PlantioRequest request) {
        Talhao talhao = encontrarTalhao(fazendaId, talhaoId);
        Cultura cultura = encontrarCultura(request.culturaId());
        LocalDate colheita = definirColheitaPrevista(cultura, request);
        String safra = definirSafra(request);
        validarSemSobreposicao(talhaoId, SEM_ID, request.dataPlantio(), colheita);

        Plantio plantio = new Plantio(talhao, cultura, request.tipoPlantio(), safra, request.dataPlantio(), colheita);
        return paraResponse(plantioRepository.save(plantio));
    }

    public List<PlantioResponse> listar(Long fazendaId, Long talhaoId, StatusPlantio status, String safra) {
        encontrarTalhao(fazendaId, talhaoId);
        Specification<Plantio> filtro = (root, query, cb) -> cb.equal(root.get("talhao").get("id"), talhaoId);
        if (status != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (safra != null && !safra.isBlank()) {
            String safraFiltro = safra.trim();
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("safra"), safraFiltro));
        }
        return plantioRepository.findAll(filtro, Sort.by(Sort.Direction.DESC, "dataPlantio"))
                .stream()
                .map(PlantioService::paraResponse)
                .toList();
    }

    /**
     * Plantios de todos os talhões da fazenda (mais recentes primeiro), com filtros opcionais de talhão, cultura,
     * estado e safra.
     */
    public List<PlantioResponse> listarDaFazenda(
            Long fazendaId, Long talhaoId, Long culturaId, StatusPlantio status, String safra
    ) {
        if (!fazendaRepository.existsById(fazendaId)) {
            throw new RecursoNaoEncontradoException("Fazenda não encontrada: " + fazendaId);
        }
        Specification<Plantio> filtro = (root, query, cb) -> cb.equal(root.get("talhao").get("fazenda").get("id"), fazendaId);
        if (talhaoId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("talhao").get("id"), talhaoId));
        }
        if (culturaId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("cultura").get("id"), culturaId));
        }
        if (status != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (safra != null && !safra.isBlank()) {
            String safraFiltro = safra.trim();
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("safra"), safraFiltro));
        }
        return plantioRepository.findAll(filtro, Sort.by(Sort.Direction.DESC, "dataPlantio").and(Sort.by("id")))
                .stream()
                .map(PlantioService::paraResponse)
                .toList();
    }

    public PlantioResponse buscar(Long fazendaId, Long talhaoId, Long plantioId) {
        return paraResponse(encontrarPlantio(fazendaId, talhaoId, plantioId));
    }

    @Transactional
    public PlantioResponse atualizar(Long fazendaId, Long talhaoId, Long plantioId, PlantioRequest request) {
        Plantio plantio = encontrarPlantio(fazendaId, talhaoId, plantioId);
        if (plantio.getStatus().finalizado()) {
            throw new ConflitoException("Plantios " + plantio.getStatus() + " não podem ser alterados.");
        }
        Cultura cultura = plantio.getCultura();
        if (!cultura.getId().equals(request.culturaId())) {
            if (plantio.getStatus() != StatusPlantio.PLANEJADO) {
                throw new ConflitoException("A cultura só pode ser trocada enquanto o plantio está PLANEJADO.");
            }
            cultura = encontrarCultura(request.culturaId());
        }
        LocalDate colheita = definirColheitaPrevista(cultura, request);
        String safra = definirSafra(request);
        validarSemSobreposicao(talhaoId, plantioId, request.dataPlantio(), colheita);

        plantio.atualizar(cultura, request.tipoPlantio(), safra, request.dataPlantio(), colheita);
        return paraResponse(plantio);
    }

    @Transactional
    public PlantioResponse alterarStatus(
            Long fazendaId,
            Long talhaoId,
            Long plantioId,
            AlterarStatusPlantioRequest request
    ) {
        Plantio plantio = encontrarPlantio(fazendaId, talhaoId, plantioId);
        StatusPlantio destino = request.status();
        if (!plantio.getStatus().podeIrPara(destino)) {
            throw new ConflitoException(
                    "Transição inválida: de " + plantio.getStatus() + " para " + destino
                            + ". Permitidas: " + plantio.getStatus().proximos() + "."
            );
        }
        boolean informouColheita = request.dataColheitaReal() != null || request.quantidadeColhidaKg() != null;
        if (destino != StatusPlantio.COLHIDO && informouColheita) {
            throw new RegraDeNegocioException(
                    "Data e quantidade colhida só podem ser informadas ao mudar o status para COLHIDO."
            );
        }

        if (destino == StatusPlantio.EM_CULTIVO) {
            if (plantioRepository.existsByTalhao_IdAndStatus(talhaoId, StatusPlantio.EM_CULTIVO)) {
                throw new ConflitoException("O talhão já possui um plantio em cultivo.");
            }
            // Reconfere: a colheita real do plantio anterior pode ter ocorrido depois do previsto.
            validarSemSobreposicao(talhaoId, plantioId, plantio.getDataPlantio(), plantio.getDataColheitaPrevista());
            plantio.alterarStatus(destino);
        } else if (destino == StatusPlantio.COLHIDO) {
            LocalDate dataColheita = request.dataColheitaReal() != null ? request.dataColheitaReal() : LocalDate.now();
            validarDataColheitaReal(plantio, dataColheita);
            plantio.colher(dataColheita, request.quantidadeColhidaKg());
        } else {
            plantio.alterarStatus(destino);
        }
        plantioRepository.flush();
        return paraResponse(plantio);
    }

    /** Corrige a data e/ou a quantidade de um plantio já colhido. */
    @Transactional
    public PlantioResponse registrarColheita(
            Long fazendaId,
            Long talhaoId,
            Long plantioId,
            RegistrarColheitaRequest request
    ) {
        Plantio plantio = encontrarPlantio(fazendaId, talhaoId, plantioId);
        if (plantio.getStatus() != StatusPlantio.COLHIDO) {
            throw new ConflitoException("A colheita só pode ser registrada em plantios COLHIDOS.");
        }
        validarDataColheitaReal(plantio, request.dataColheitaReal());
        plantio.registrarColheita(request.dataColheitaReal(), request.quantidadeColhidaKg());
        plantioRepository.flush();
        return paraResponse(plantio);
    }

    @Transactional
    public void excluir(Long fazendaId, Long talhaoId, Long plantioId) {
        Plantio plantio = encontrarPlantio(fazendaId, talhaoId, plantioId);
        if (plantio.getStatus() != StatusPlantio.PLANEJADO) {
            throw new ConflitoException(
                    "Somente plantios PLANEJADOS podem ser excluídos; use a mudança de status para cancelar."
            );
        }
        plantioRepository.delete(plantio);
        plantioRepository.flush();
    }

    private static void validarDataColheitaReal(Plantio plantio, LocalDate dataColheita) {
        if (dataColheita.isBefore(plantio.getDataPlantio())) {
            throw new RegraDeNegocioException("A data real de colheita não pode ser anterior à data de plantio.");
        }
        if (dataColheita.isAfter(LocalDate.now())) {
            throw new RegraDeNegocioException("A data real de colheita não pode estar no futuro.");
        }
    }

    /** Só é possível plantar depois da colheita do plantio anterior do mesmo talhão. */
    private void validarSemSobreposicao(Long talhaoId, Long plantioIgnoradoId, LocalDate inicio, LocalDate fim) {
        LocalDate fimEfetivo = fim != null ? fim : SEM_FIM;
        List<Plantio> conflitantes = plantioRepository.buscarConflitantes(
                talhaoId, plantioIgnoradoId, inicio, fimEfetivo, SEM_FIM, StatusPlantio.CANCELADO
        );
        if (conflitantes.isEmpty()) {
            return;
        }
        Plantio outro = conflitantes.get(0);
        LocalDate fimOutro = outro.getDataColheitaReal() != null
                ? outro.getDataColheitaReal()
                : outro.getDataColheitaPrevista();
        String periodoOutro = fimOutro != null
                ? outro.getDataPlantio() + " a " + fimOutro
                : "desde " + outro.getDataPlantio() + " (sem colheita prevista)";
        throw new ConflitoException(
                "O período conflita com o plantio " + outro.getId() + " (" + outro.getCultura().getNome()
                        + ", " + periodoOutro + "). Só é possível plantar depois da colheita anterior"
                        + (fim == null ? "; informe a data de colheita prevista para planejar este plantio." : ".")
        );
    }

    /** Usa a data informada ou, se houver ciclo cadastrado na cultura, plantio + ciclo. */
    private static LocalDate definirColheitaPrevista(Cultura cultura, PlantioRequest request) {
        LocalDate colheita = request.dataColheitaPrevista();
        if (colheita == null && cultura.getCicloDias() != null) {
            colheita = request.dataPlantio().plusDays(cultura.getCicloDias());
        }
        if (colheita != null && colheita.isBefore(request.dataPlantio())) {
            throw new RegraDeNegocioException(
                    "A data de colheita prevista não pode ser anterior à data de plantio."
            );
        }
        return colheita;
    }

    private static String definirSafra(PlantioRequest request) {
        if (request.safra() == null || request.safra().isBlank()) {
            return safraPadrao(request.dataPlantio());
        }
        String safra = request.safra().trim();
        int inicio = Integer.parseInt(safra.substring(0, 4));
        int fim = Integer.parseInt(safra.substring(5, 9));
        if (fim != inicio + 1) {
            throw new RegraDeNegocioException(
                    "Safra inválida: use anos consecutivos no formato AAAA/AAAA, por exemplo 2026/2027."
            );
        }
        return safra;
    }

    static String safraPadrao(LocalDate dataPlantio) {
        int ano = dataPlantio.getYear();
        return dataPlantio.getMonthValue() >= MES_INICIO_SAFRA
                ? ano + "/" + (ano + 1)
                : (ano - 1) + "/" + ano;
    }

    private Talhao encontrarTalhao(Long fazendaId, Long talhaoId) {
        return talhaoRepository.findByIdAndFazenda_Id(talhaoId, fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Talhão não encontrado na fazenda informada: " + talhaoId
                ));
    }

    private Cultura encontrarCultura(Long culturaId) {
        return culturaRepository.findById(culturaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cultura não encontrada: " + culturaId));
    }

    private Plantio encontrarPlantio(Long fazendaId, Long talhaoId, Long plantioId) {
        encontrarTalhao(fazendaId, talhaoId);
        return plantioRepository.findByIdAndTalhao_Id(plantioId, talhaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Plantio não encontrado no talhão informado: " + plantioId
                ));
    }

    private static PlantioResponse paraResponse(Plantio plantio) {
        return new PlantioResponse(
                plantio.getId(),
                plantio.getTalhao().getId(),
                plantio.getCultura().getId(),
                plantio.getCultura().getNome(),
                plantio.getTipoPlantio(),
                plantio.getSafra(),
                plantio.getDataPlantio(),
                plantio.getDataColheitaPrevista(),
                plantio.getDataColheitaReal(),
                plantio.getQuantidadeColhidaKg(),
                calcularProdutividade(plantio),
                plantio.getStatus()
        );
    }

    private static Double calcularProdutividade(Plantio plantio) {
        Double quantidade = plantio.getQuantidadeColhidaKg();
        Double area = plantio.getTalhao().getAreaHectares();
        if (quantidade == null || area == null || area <= 0) {
            return null;
        }
        return Math.round(quantidade / area * 100.0) / 100.0;
    }
}
