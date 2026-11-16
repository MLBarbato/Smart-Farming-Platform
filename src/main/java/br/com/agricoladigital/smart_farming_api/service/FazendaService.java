package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.FazendaRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.FazendaResponse;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.PrevisaoTempoRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FazendaService {
    private final FazendaRepository fazendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TalhaoRepository talhaoRepository;
    private final PrevisaoTempoRepository previsaoTempoRepository;
    private final EstacaoCadastro estacaoCadastro;

    public FazendaService(
            FazendaRepository fazendaRepository,
            UsuarioRepository usuarioRepository,
            TalhaoRepository talhaoRepository,
            PrevisaoTempoRepository previsaoTempoRepository,
            EstacaoCadastro estacaoCadastro
    ) {
        this.fazendaRepository = fazendaRepository;
        this.usuarioRepository = usuarioRepository;
        this.talhaoRepository = talhaoRepository;
        this.previsaoTempoRepository = previsaoTempoRepository;
        this.estacaoCadastro = estacaoCadastro;
    }

    @Transactional
    public FazendaResponse criar(Long usuarioId, FazendaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + usuarioId));
        Fazenda fazenda = new Fazenda(
                request.nome(),
                request.localizacao(),
                request.areaTotal(),
                request.latitude(),
                request.longitude(),
                request.cidade(),
                request.estado(),
                usuario
        );
        return paraResponse(fazendaRepository.save(fazenda));
    }

    public List<FazendaResponse> listarPorUsuario(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new RecursoNaoEncontradoException("Usuário não encontrado: " + usuarioId);
        }
        return fazendaRepository.findAllByUsuario_Id(usuarioId)
                .stream()
                .map(FazendaService::paraResponse)
                .toList();
    }

    public List<FazendaResponse> listar() {
        return fazendaRepository.findAll(Sort.by("nome"))
                .stream()
                .map(FazendaService::paraResponse)
                .toList();
    }

    public FazendaResponse buscar(Long id) {
        return paraResponse(encontrarFazenda(id));
    }

    @Transactional
    public FazendaResponse atualizar(Long id, FazendaRequest request) {
        Fazenda fazenda = encontrarFazenda(id);
        fazenda.atualizar(
                request.nome(),
                request.localizacao(),
                request.areaTotal(),
                request.latitude(),
                request.longitude(),
                request.cidade(),
                request.estado()
        );
        return paraResponse(fazenda);
    }

    @Transactional
    public void excluir(Long id) {
        Fazenda fazenda = encontrarFazenda(id);
        if (talhaoRepository.existsByFazenda_Id(id)) {
            throw new ConflitoException("Não é possível excluir a fazenda enquanto houver talhões vinculados.");
        }
        // Previsão e estação virtual são dados derivados do provedor externo e saem com a fazenda.
        previsaoTempoRepository.deleteAllByFazenda_Id(id);
        estacaoCadastro.removerSeExistir(id);
        fazendaRepository.delete(fazenda);
        fazendaRepository.flush();
    }

    private Fazenda encontrarFazenda(Long id) {
        return fazendaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fazenda não encontrada: " + id));
    }

    private static FazendaResponse paraResponse(Fazenda fazenda) {
        return new FazendaResponse(
                fazenda.getId(),
                fazenda.getNome(),
                fazenda.getLocalizacao(),
                fazenda.getAreaTotal(),
                fazenda.getUsuario().getId(),
                Boolean.TRUE.equals(fazenda.getUsuario().getAtivo()),
                fazenda.getLatitude(),
                fazenda.getLongitude(),
                fazenda.getCidade(),
                fazenda.getEstado()
        );
    }
}
