package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarSenhaRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.AtualizarUsuarioRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.CriarUsuarioRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.UsuarioResponse;
import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.SenhaAtualInvalidaException;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UsuarioResponse criar(CriarUsuarioRequest request) {
        String email = normalizarEmail(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflitoException("Já existe um usuário cadastrado com este e-mail.");
        }

        Usuario usuario = new Usuario(
                request.nome(),
                email,
                passwordEncoder.encode(request.senha()),
                request.perfil()
        );
        return paraResponse(usuarioRepository.save(usuario));
    }

    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll(Sort.by("nome"))
                .stream()
                .map(UsuarioService::paraResponse)
                .toList();
    }

    public UsuarioResponse buscar(Long id) {
        return paraResponse(encontrarUsuario(id));
    }

    @Transactional
    public UsuarioResponse atualizar(
            Long id,
            AtualizarUsuarioRequest request,
            Long solicitanteId,
            boolean solicitanteAdministrador
    ) {
        Usuario usuario = encontrarUsuario(id);
        if (request.perfil() != usuario.getPerfil()) {
            if (!solicitanteAdministrador) {
                throw new AccessDeniedException("Somente administradores podem alterar o perfil.");
            }
            if (id.equals(solicitanteId)) {
                throw new ConflitoException("Não é possível alterar o próprio perfil.");
            }
        }
        String email = normalizarEmail(request.email());
        if (!usuario.getEmail().equals(email) && usuarioRepository.existsByEmail(email)) {
            throw new ConflitoException("Já existe um usuário cadastrado com este e-mail.");
        }
        usuario.atualizarDados(request.nome(), email, request.perfil());
        return paraResponse(usuario);
    }

    @Transactional
    public void alterarSenha(Long id, AlterarSenhaRequest request) {
        Usuario usuario = encontrarUsuario(id);
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new SenhaAtualInvalidaException();
        }
        usuario.alterarSenha(passwordEncoder.encode(request.novaSenha()));
    }

    @Transactional
    public void desativar(Long id, Long solicitanteId) {
        Usuario usuario = encontrarUsuario(id);
        if (usuario.getPerfil() == PerfilUsuario.ADMINISTRADOR && id.equals(solicitanteId)) {
            throw new ConflitoException("Administradores não podem desativar a própria conta.");
        }
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new ConflitoException("O usuário já está desativado.");
        }
        usuario.desativar();
        refreshTokenService.revogarTodos(id);
    }

    @Transactional
    public void ativar(Long id) {
        Usuario usuario = encontrarUsuario(id);
        if (Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new ConflitoException("O usuário já está ativo.");
        }
        usuario.ativar();
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Usuario encontrarUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + id));
    }

    static UsuarioResponse paraResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(), usuario.getAtivo());
    }
}
