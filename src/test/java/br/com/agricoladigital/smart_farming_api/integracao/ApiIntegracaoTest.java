package br.com.agricoladigital.smart_farming_api.integracao;

import br.com.agricoladigital.smart_farming_api.entity.PerfilUsuario;
import br.com.agricoladigital.smart_farming_api.entity.Usuario;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.service.AvaliadorAlertas;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A API inteira (segurança JWT, validação, serviços, JPA e Flyway) sobre PostgreSQL real, percorrendo a
 * jornada do agricultor: cadastro, fazenda, talhão, plantio, sensor, alerta, atendimento e recomendação.
 */
@AutoConfigureMockMvc
class ApiIntegracaoTest extends IntegracaoPostgresBase {
    private static final String SENHA = "senha-segura-123";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AvaliadorAlertas avaliador;

    private String json(MockHttpServletRequestBuilder req, String token, String corpo) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        MvcResult resultado = mvc.perform(req).andReturn();
        return resultado.getResponse().getContentAsString();
    }

    private String login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
    }

    private void registrar(String nome, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isCreated());
    }

    private String criarAdmin() throws Exception {
        usuarioRepository.save(new Usuario("Admin", "admin@example.com", passwordEncoder.encode(SENHA), PerfilUsuario.ADMINISTRADOR));
        return login("admin@example.com");
    }

    @Test
    void jornadaDoAgricultorDoCadastroAoAtendimentoDoAlerta() throws Exception {
        String admin = criarAdmin();
        registrar("Ana", "ana@example.com");
        registrar("Bia", "bia@example.com");
        String ana = login("ana@example.com");
        String bia = login("bia@example.com");
        Long anaId = usuarioRepository.findAll().stream().filter(u -> u.getEmail().equals("ana@example.com"))
                .findFirst().orElseThrow().getId();

        // cadastro público só cria agricultores; rotas de administração são protegidas
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + ana)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/fazendas")).andExpect(status().isUnauthorized());

        // catálogo de culturas: o administrador cria, com parâmetros de irrigação validados
        mvc.perform(post("/api/v1/culturas").header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Soja\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/culturas").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Soja\",\"umidadeMinima\":30,\"umidadeIdeal\":20,\"profundidadeRaizCm\":30}"))
                .andExpect(status().isBadRequest());
        String cultura = json(post("/api/v1/culturas"), admin,
                "{\"nome\":\"Soja\",\"cicloDias\":120,\"umidadeMinima\":30,\"umidadeIdeal\":38,\"profundidadeRaizCm\":30}");
        Integer culturaId = JsonPath.read(cultura, "$.id");

        // fazenda, talhão e plantio
        String fazenda = json(post("/api/v1/usuarios/" + anaId + "/fazendas"), ana,
                "{\"nome\":\"Fazenda Ana\",\"areaTotal\":100,\"latitude\":-17.79,\"longitude\":-50.91}");
        Integer fazendaId = JsonPath.read(fazenda, "$.id");
        String talhao = json(post("/api/v1/fazendas/" + fazendaId + "/talhoes"), ana,
                "{\"identificacao\":\"T1\",\"areaHectares\":10,\"geometria\":{\"type\":\"Polygon\",\"coordinates\":"
                        + "[[[-50.93,-17.8],[-50.91,-17.8],[-50.91,-17.78],[-50.93,-17.8]]]}}");
        Integer talhaoId = JsonPath.read(talhao, "$.id");
        String plantio = json(post("/api/v1/fazendas/" + fazendaId + "/talhoes/" + talhaoId + "/plantios"), ana,
                "{\"culturaId\":" + culturaId + ",\"safra\":\"2026/2027\",\"dataPlantio\":\"2026-10-01\"}");
        Integer plantioId = JsonPath.read(plantio, "$.id");
        mvc.perform(patch("/api/v1/fazendas/" + fazendaId + "/talhoes/" + talhaoId + "/plantios/" + plantioId + "/status")
                        .header("Authorization", "Bearer " + ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_CULTIVO\"}"))
                .andExpect(status().isOk());

        // outro agricultor não enxerga nem altera a fazenda
        mvc.perform(get("/api/v1/fazendas/" + fazendaId + "/mapa").header("Authorization", "Bearer " + bia))
                .andExpect(status().isForbidden());

        // sensor de solo e leituras (o simulador está desligado: entram direto no banco)
        String sensor = json(post("/api/v1/fazendas/" + fazendaId + "/talhoes/" + talhaoId + "/sensores"), ana,
                "{\"codigo\":\"SOLO-01\",\"variavel\":\"UMIDADE_SOLO\"}");
        Integer sensorId = JsonPath.read(sensor, "$.id");
        LocalDateTime agora = agoraUtc();
        Sensor entidade = sensorRepository.findById(sensorId.longValue()).orElseThrow();
        for (int minutos : new int[]{5, 40, 90}) {
            leitura(entidade, 18.0, agora.minusMinutes(minutos));
        }
        mvc.perform(get("/api/v1/fazendas/" + fazendaId + "/talhoes/" + talhaoId + "/sensores/" + sensorId + "/leituras/ultima")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(18.0));

        // o motor de avaliação gera o alerta; a API o lista, mostra no mapa e no resumo
        avaliador.avaliarFazenda(fazendaId.longValue());
        String alertas = json(get("/api/v1/fazendas/" + fazendaId + "/alertas?status=ABERTO&criticidade=ALTA"), ana, null);
        Integer alertaId = JsonPath.read(alertas, "$.conteudo[0].id");
        mvc.perform(get("/api/v1/fazendas/" + fazendaId + "/alertas?status=ABERTO").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].evento").value("SECA"))
                .andExpect(jsonPath("$.conteudo[0].talhaoIdentificacao").value("T1"))
                .andExpect(jsonPath("$.conteudo[0].leitura.dispositivo").value("SOLO-01"));
        mvc.perform(get("/api/v1/fazendas/" + fazendaId + "/mapa").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.talhoes[0].alertas.maiorCriticidade").value("ALTA"))
                .andExpect(jsonPath("$.talhoes[0].geometria.type").value("Polygon"))
                .andExpect(jsonPath("$.alertas.abertos").value(1));
        mvc.perform(get("/api/v1/resumo").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totais.alertas.alta").value(1));
        mvc.perform(get("/api/v1/fazendas/" + fazendaId + "/alertas").header("Authorization", "Bearer " + bia))
                .andExpect(status().isForbidden());

        // atendimento: assumir, transição inválida e resolver
        String base = "/api/v1/fazendas/" + fazendaId + "/alertas/" + alertaId + "/status";
        mvc.perform(patch(base).header("Authorization", "Bearer " + ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_ATENDIMENTO\",\"nota\":\"irrigando\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.atendente.nome").value("Ana"));
        mvc.perform(patch(base).header("Authorization", "Bearer " + ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ABERTO\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(base).header("Authorization", "Bearer " + bia).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(base).header("Authorization", "Bearer " + ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISPENSADO\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(base).header("Authorization", "Bearer " + ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVIDO\",\"nota\":\"irrigado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolucao").value("MANUAL"));

        // sem previsão cadastrada, o solo seco não gera sugestão com falsa certeza
        mvc.perform(post("/api/v1/fazendas/" + fazendaId + "/recomendacoes-irrigacao/calcular")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].situacao").value("SEM_DADOS_SUFICIENTES"))
                .andExpect(jsonPath("$[0].volumeRecomendadoMm").doesNotExist())
                .andExpect(jsonPath("$[0].dadosFaltantes[0]", containsString("previsão")));
        mvc.perform(get("/api/v1/fazendas/" + fazendaId + "/resumo").header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.irrigacao.semDadosSuficientes").value(1));

        // o administrador enxerga a fazenda de qualquer usuário
        mvc.perform(get("/api/v1/fazendas/" + fazendaId + "/alertas").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenRotacionaEOTokenAntigoPassaAInvalido() throws Exception {
        registrar("Ana", "ana@example.com");
        MvcResult tokens = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String refresh = JsonPath.read(tokens.getResponse().getContentAsString(), "$.refreshToken");

        MvcResult novo = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String novoRefresh = JsonPath.read(novo.getResponse().getContentAsString(), "$.refreshToken");
        assertTrue(!novoRefresh.equals(refresh));

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioDesativadoEBarradoMesmoComTokenValido() throws Exception {
        String admin = criarAdmin();
        registrar("Ana", "ana@example.com");
        String ana = login("ana@example.com");
        Long anaId = usuarioRepository.findAll().stream().filter(u -> u.getEmail().equals("ana@example.com"))
                .findFirst().orElseThrow().getId();

        mvc.perform(patch("/api/v1/usuarios/" + anaId + "/desativar").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/usuarios/" + anaId).header("Authorization", "Bearer " + ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_DESATIVADO"));
    }
}
