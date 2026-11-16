package br.com.agricoladigital.smart_farming_api.integracao;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Base dos testes de integração: aplicação completa contra um PostgreSQL real (Testcontainers), com as
 * migrações Flyway aplicadas e {@code ddl-auto=validate}. O contêiner é único para todas as classes
 * (sobe uma vez) e os testes são ignorados quando não há Docker. Os jobs agendados ficam desligados;
 * cada teste começa com as tabelas de dados vazias (as regras de alerta semeadas permanecem).
 */
@SpringBootTest
@EnabledIf("br.com.agricoladigital.smart_farming_api.integracao.IntegracaoPostgresBase#dockerDisponivel")
public abstract class IntegracaoPostgresBase {
    private static PostgreSQLContainer postgres;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError erro) {
            return false;
        }
    }

    private static synchronized PostgreSQLContainer postgres() {
        if (postgres == null) {
            // mesma versão do docker-compose.yaml; para testar outra: -Dpostgres.test.image=postgres:18
            postgres = new PostgreSQLContainer(System.getProperty("postgres.test.image", "postgres:16"));
            postgres.start();
        }
        return postgres;
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        PostgreSQLContainer pg = postgres();
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("app.jwt.secret", () -> "segredo-de-teste-de-integracao-com-mais-de-32-caracteres");
        registry.add("app.previsao.atualizacao-automatica", () -> "false");
        registry.add("app.estacao.atualizacao-automatica", () -> "false");
        registry.add("app.alertas.avaliacao-automatica", () -> "false");
        registry.add("app.irrigacao.calculo-automatico", () -> "false");
        registry.add("app.simulador.enabled", () -> "false");
        registry.add("app.admin.email", () -> "");
        registry.add("app.admin.password", () -> "");
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected FazendaRepository fazendaRepository;
    @Autowired protected TalhaoRepository talhaoRepository;
    @Autowired protected CulturaRepository culturaRepository;
    @Autowired protected PlantioRepository plantioRepository;
    @Autowired protected DispositivoRepository dispositivoRepository;
    @Autowired protected SensorRepository sensorRepository;
    @Autowired protected LeituraRepository leituraRepository;
    @Autowired protected AlertaRepository alertaRepository;
    @Autowired protected RegraAlertaRepository regraRepository;

    protected static String jdbcUrl() {
        return postgres().getJdbcUrl();
    }

    protected static String jdbcUsuario() {
        return postgres().getUsername();
    }

    protected static String jdbcSenha() {
        return postgres().getPassword();
    }

    @BeforeEach
    void limparDados() {
        jdbc.execute("TRUNCATE TABLE alerta, recomendacao_irrigacao, leitura, sensor, estacao_meteorologica, "
                + "dispositivo, previsao_tempo, plantio, talhao, cultura, fazenda, refresh_token, usuario "
                + "RESTART IDENTITY CASCADE");
    }

    // ---- construção de dados ----

    private int sequencia;

    protected Usuario novoUsuario(PerfilUsuario perfil) {
        int n = ++sequencia;
        return usuarioRepository.save(new Usuario("Usuario " + n, "u" + n + "-" + System.nanoTime() + "@example.com",
                "hash", perfil));
    }

    protected Fazenda novaFazenda(Usuario dono) {
        return fazendaRepository.save(new Fazenda("Fazenda " + (++sequencia), "GO", 100.0, -17.79, -50.91,
                "Rio Verde", "GO", dono));
    }

    protected Talhao novoTalhao(Fazenda fazenda, String identificacao) {
        return talhaoRepository.save(new Talhao(identificacao, 10.0, null, fazenda));
    }

    protected Cultura novaCultura(String nome, Double minima, Double ideal, Integer raizCm) {
        return culturaRepository.save(new Cultura(nome + (++sequencia), 120, minima, ideal, raizCm));
    }

    /** Plantio já em cultivo. */
    protected Plantio plantioEmCultivo(Talhao talhao, Cultura cultura) {
        Plantio plantio = new Plantio(talhao, cultura, "Direto", "2026/2027", LocalDate.now().minusDays(30), null);
        org.springframework.test.util.ReflectionTestUtils.setField(plantio, "status", StatusPlantio.EM_CULTIVO);
        return plantioRepository.save(plantio);
    }

    protected Sensor novoSensor(Talhao talhao, VariavelMonitorada variavel) {
        Dispositivo dispositivo = dispositivoRepository.save(new Dispositivo(
                "S" + (++sequencia) + "-" + System.nanoTime(), "SENSOR", LocalDate.now().minusDays(10),
                StatusDispositivo.ATIVO, talhao));
        return sensorRepository.save(new Sensor(dispositivo, variavel));
    }

    protected Leitura leitura(Sensor sensor, double valor, LocalDateTime dataHoraUtc) {
        return leituraRepository.save(new Leitura(sensor.getDispositivo(), valor, dataHoraUtc, OrigemLeitura.SIMULADA));
    }

    protected static LocalDateTime agoraUtc() {
        return LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
    }

    protected RegraAlerta regra(EventoAlerta evento, CriticidadeAlerta criticidade) {
        return regraRepository.findAllByEvento(evento).stream()
                .filter(r -> r.getCriticidade() == criticidade).findFirst().orElseThrow();
    }
}
