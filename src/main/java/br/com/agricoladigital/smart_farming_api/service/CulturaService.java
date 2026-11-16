package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.CulturaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.CulturaResponse;
import br.com.agricoladigital.smart_farming_api.entity.Cultura;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.CulturaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PlantioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CulturaService {
    private static final String MENSAGEM_NOME_DUPLICADO = "Já existe uma cultura cadastrada com este nome.";

    private final CulturaRepository culturaRepository;
    private final PlantioRepository plantioRepository;

    public CulturaService(CulturaRepository culturaRepository, PlantioRepository plantioRepository) {
        this.culturaRepository = culturaRepository;
        this.plantioRepository = plantioRepository;
    }

    @Transactional
    public CulturaResponse criar(CulturaRequest request) {
        String nome = request.nome().trim();
        if (culturaRepository.existsByNomeIgnoreCase(nome)) {
            throw new ConflitoException(MENSAGEM_NOME_DUPLICADO);
        }
        validarParametrosDeIrrigacao(request);
        Cultura cultura = new Cultura(nome, request.cicloDias(), request.umidadeMinima(), request.umidadeIdeal(),
                request.profundidadeRaizCm());
        return paraResponse(culturaRepository.save(cultura));
    }

    public List<CulturaResponse> listar() {
        return culturaRepository.findAll(Sort.by("nome"))
                .stream()
                .map(CulturaService::paraResponse)
                .toList();
    }

    public CulturaResponse buscar(Long id) {
        return paraResponse(encontrar(id));
    }

    @Transactional
    public CulturaResponse atualizar(Long id, CulturaRequest request) {
        Cultura cultura = encontrar(id);
        String nome = request.nome().trim();
        if (culturaRepository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ConflitoException(MENSAGEM_NOME_DUPLICADO);
        }
        validarParametrosDeIrrigacao(request);
        cultura.atualizar(nome, request.cicloDias(), request.umidadeMinima(), request.umidadeIdeal(),
                request.profundidadeRaizCm());
        return paraResponse(cultura);
    }

    @Transactional
    public void excluir(Long id) {
        Cultura cultura = encontrar(id);
        if (plantioRepository.existsByCultura_Id(id)) {
            throw new ConflitoException("Não é possível excluir a cultura enquanto houver plantios vinculados.");
        }
        culturaRepository.delete(cultura);
        culturaRepository.flush();
    }

    private static void validarParametrosDeIrrigacao(CulturaRequest request) {
        int informados = (request.umidadeMinima() != null ? 1 : 0) + (request.umidadeIdeal() != null ? 1 : 0)
                + (request.profundidadeRaizCm() != null ? 1 : 0);
        if (informados != 0 && informados != 3) {
            throw new RegraDeNegocioException(
                    "Informe umidadeMinima, umidadeIdeal e profundidadeRaizCm juntas, ou nenhuma delas.");
        }
        if (informados == 3 && request.umidadeMinima() >= request.umidadeIdeal()) {
            throw new RegraDeNegocioException("umidadeMinima deve ser menor que umidadeIdeal.");
        }
    }

    private Cultura encontrar(Long id) {
        return culturaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cultura não encontrada: " + id));
    }

    private static CulturaResponse paraResponse(Cultura cultura) {
        return new CulturaResponse(
                cultura.getId(), cultura.getNome(), cultura.getCicloDias(), cultura.getUmidadeMinima(),
                cultura.getUmidadeIdeal(), cultura.getProfundidadeRaizCm(), cultura.temParametrosDeIrrigacao()
        );
    }
}
