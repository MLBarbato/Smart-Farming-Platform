package br.com.agricoladigital.smart_farming_api.config;

import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Cria o primeiro administrador a partir de ADMIN_EMAIL/ADMIN_PASSWORD, se ainda não houver nenhum. */
@Component
public class AdminInicializador implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInicializador.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String nome;
    private final String email;
    private final String senha;

    public AdminInicializador(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.nome}") String nome,
            @Value("${app.admin.email}") String email,
            @Value("${app.admin.password}") String senha
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByPerfil(PerfilUsuario.ADMINISTRADOR)) {
            return;
        }
        if (email.isBlank() || senha.isBlank()) {
            log.warn("Nenhum administrador cadastrado e ADMIN_EMAIL/ADMIN_PASSWORD não definidos.");
            return;
        }
        if (senha.length() < 8 || senha.length() > 72) {
            log.warn("ADMIN_PASSWORD deve ter entre 8 e 72 caracteres; administrador não criado.");
            return;
        }
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            log.warn("O e-mail de ADMIN_EMAIL já pertence a um usuário não administrador; administrador não criado.");
            return;
        }
        usuarioRepository.save(new Usuario(
                nome, emailNormalizado, passwordEncoder.encode(senha), PerfilUsuario.ADMINISTRADOR
        ));
        log.info("Administrador inicial criado.");
    }
}
