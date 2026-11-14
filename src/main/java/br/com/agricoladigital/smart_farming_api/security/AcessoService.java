package br.com.agricoladigital.smart_farming_api.security;

import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/** Regras de propriedade usadas nas expressões @PreAuthorize (bean "acesso"). */
@Component("acesso")
public class AcessoService {
    private final FazendaRepository fazendaRepository;

    public AcessoService(FazendaRepository fazendaRepository) {
        this.fazendaRepository = fazendaRepository;
    }

    public static boolean ehAdministrador(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + PerfilUsuario.ADMINISTRADOR.name())::equals);
    }

    public static Long idDoUsuario(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    public boolean ehProprioUsuario(Authentication authentication, Long usuarioId) {
        return authentication != null && usuarioId != null && usuarioId.equals(idDoUsuario(authentication));
    }

    public boolean podeAcessarUsuario(Authentication authentication, Long usuarioId) {
        return ehAdministrador(authentication) || ehProprioUsuario(authentication, usuarioId);
    }

    public boolean podeAcessarFazenda(Authentication authentication, Long fazendaId) {
        if (ehAdministrador(authentication)) {
            return true;
        }
        return fazendaId != null
                && fazendaRepository.existsByIdAndUsuario_Id(fazendaId, idDoUsuario(authentication));
    }
}
