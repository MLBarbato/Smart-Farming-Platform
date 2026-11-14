package br.com.agricoladigital.smart_farming_api.security;

import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.exception.ContaDesativadaException;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converte o JWT em autenticação consultando o usuário a cada requisição: o perfil vem do
 * banco (não do token) e usuários desativados perdem o acesso imediatamente.
 */
@Component
public class UsuarioAtivoJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UsuarioRepository usuarioRepository;

    public UsuarioAtivoJwtConverter(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long usuarioId = extrairId(jwt);
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuário do token não existe mais."));
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new ContaDesativadaException();
        }
        return new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())),
                usuario.getId().toString()
        );
    }

    private static Long extrairId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Token com identificação inválida.");
        }
    }
}
