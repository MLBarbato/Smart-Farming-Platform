package br.com.agricoladigital.smart_farming_api.integracao;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audita o contrato OpenAPI gerado: cada operação precisa de resumo e tag, as rotas protegidas declaram
 * 401/403 com o formato de erro, e os DTOs de entrada trazem exemplo. Grava o contrato em
 * {@code target/openapi.json}.
 */
@AutoConfigureMockMvc
class OpenApiIntegracaoTest extends IntegracaoPostgresBase {
    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired private MockMvc mvc;
    private DocumentContext api;
    private Map<String, Object> caminhos;

    @BeforeEach
    void carregar() throws Exception {
        String corpo = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        api = JsonPath.parse(corpo);
        caminhos = api.read("$.paths");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), corpo);
    }

    /** Todas as operações como "METODO /caminho" para o objeto da operação. */
    private Map<String, Map<String, Object>> operacoes() {
        Map<String, Map<String, Object>> todas = new TreeMap<>();
        caminhos.forEach((caminho, item) -> ((Map<String, Object>) item).forEach((metodo, operacao) -> {
            if (METODOS.contains(metodo)) {
                todas.put(metodo.toUpperCase() + " " + caminho, (Map<String, Object>) operacao);
            }
        }));
        return todas;
    }

    @Test
    void documentacaoEPublicaEDescreveAApi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        assertEquals("Smart Farming API", api.read("$.info.title"));
        String descricao = api.read("$.info.description");
        assertTrue(descricao.contains("Authorization: Bearer"), "explica a autenticação");
        assertTrue(descricao.contains("Open-Meteo"), "explica a atribuição dos dados externos");
        assertTrue(descricao.contains("demonstração"), "avisa que as regras são de demonstração");
        assertEquals("http", api.read("$.components.securitySchemes.bearerAuth.type"));
    }

    @Test
    void todaOperacaoTemResumoTagEIdentificadorUnico() {
        Map<String, Map<String, Object>> operacoes = operacoes();
        assertTrue(operacoes.size() >= 60, "operações documentadas: " + operacoes.size());
        Set<String> ids = new HashSet<>();
        List<String> semResumo = new ArrayList<>();
        List<String> semTag = new ArrayList<>();
        operacoes.forEach((chave, op) -> {
            if (op.get("summary") == null || op.get("summary").toString().isBlank()) {
                semResumo.add(chave);
            }
            List<?> tags = (List<?>) op.get("tags");
            if (tags == null || tags.isEmpty() || "".equals(tags.get(0))) {
                semTag.add(chave);
            }
            assertTrue(ids.add(String.valueOf(op.get("operationId"))), "operationId repetido: " + op.get("operationId"));
        });
        assertEquals(List.of(), semResumo, "operações sem resumo");
        assertEquals(List.of(), semTag, "operações sem tag");
    }

    @Test
    void rotasProtegidasDeclaram401EAsPublicasNaoExigemToken() {
        List<String> semErro = new ArrayList<>();
        operacoes().forEach((chave, op) -> {
            boolean publica = chave.contains("/api/v1/auth/");
            Map<String, Object> respostas = (Map<String, Object>) op.get("responses");
            if (publica) {
                assertEquals(List.of(), op.get("security"), chave + " não deve exigir token");
                assertFalse(respostas.containsKey("401") && chave.endsWith("registro"), chave);
            } else if (!respostas.containsKey("401")) {
                semErro.add(chave);
            }
        });
        assertEquals(List.of(), semErro, "rotas protegidas sem 401");
    }

    @Test
    void todaOperacaoDeclaraUmaRespostaDeSucesso() {
        List<String> semSucesso = new ArrayList<>();
        operacoes().forEach((chave, op) -> {
            Map<String, Object> respostas = (Map<String, Object>) op.get("responses");
            if (respostas.keySet().stream().noneMatch(c -> c.startsWith("2"))) {
                semSucesso.add(chave);
            }
        });
        assertEquals(List.of(), semSucesso, "operações sem resposta 2xx");
        // o sucesso traz o esquema do retorno (e os criados respondem 201; as remoções, 204)
        assertEquals("#/components/schemas/AlertaResponse",
                api.read("$.paths['/api/v1/fazendas/{fazendaId}/alertas/{alertaId}/status'].patch.responses['200'].content['*/*'].schema['$ref']"));
        assertNotNull(api.read("$.paths['/api/v1/fazendas/{fazendaId}/talhoes'].post.responses['201']"));
        assertNotNull(api.read("$.paths['/api/v1/fazendas/{id}'].delete.responses['204']"));
    }

    @Test
    void respostasDeErroUsamOFormatoPadrao() {
        Map<String, Object> excluirFazenda = api.read("$.paths['/api/v1/fazendas/{id}'].delete.responses");
        assertTrue(excluirFazenda.containsKey("403"));
        assertTrue(excluirFazenda.containsKey("404"));
        assertTrue(excluirFazenda.containsKey("409"));
        assertEquals("#/components/schemas/ErroResponse",
                api.read("$.paths['/api/v1/fazendas/{id}'].delete.responses['409'].content['application/json'].schema['$ref']"));
        assertNotNull(api.read("$.components.schemas.ErroResponse.properties.mensagem"));

        Map<String, Object> login = api.read("$.paths['/api/v1/auth/login'].post.responses");
        assertTrue(login.containsKey("429"), "login bloqueado");
        Map<String, Object> previsao = api.read("$.paths['/api/v1/fazendas/{fazendaId}/previsao/atualizar'].post.responses");
        assertTrue(previsao.containsKey("503"), "provedor externo indisponível");
        Map<String, Object> alterarRegra = api.read("$.paths['/api/v1/regras-alerta/{id}'].put.responses");
        assertTrue(alterarRegra.containsKey("403"), "somente administrador");
    }

    @Test
    void parametrosDeCaminhoEDeFiltroSaoDescritos() {
        List<String> semDescricao = new ArrayList<>();
        operacoes().forEach((chave, op) -> {
            List<Map<String, Object>> parametros = (List<Map<String, Object>>) op.get("parameters");
            if (parametros != null) {
                parametros.forEach(p -> {
                    if (p.get("description") == null && !"authentication".equals(p.get("name"))) {
                        semDescricao.add(chave + " -> " + p.get("name"));
                    }
                });
            }
        });
        assertEquals(List.of(), semDescricao, "parâmetros sem descrição");
    }

    @Test
    void rotasPrincipaisDosModulosEstaoPresentes() {
        for (String rota : List.of(
                "/api/v1/auth/login", "/api/v1/usuarios", "/api/v1/fazendas/{id}", "/api/v1/culturas/{id}",
                "/api/v1/fazendas/{fazendaId}/talhoes/{talhaoId}/plantios/{plantioId}/status",
                "/api/v1/fazendas/{fazendaId}/talhoes/{talhaoId}/sensores",
                "/api/v1/fazendas/{fazendaId}/talhoes/{talhaoId}/leituras/atuais",
                "/api/v1/fazendas/{fazendaId}/mapa", "/api/v1/resumo", "/api/v1/fazendas/{fazendaId}/resumo",
                "/api/v1/fazendas/{fazendaId}/previsao", "/api/v1/fazendas/{fazendaId}/estacao",
                "/api/v1/regras-alerta", "/api/v1/fazendas/{fazendaId}/alertas",
                "/api/v1/fazendas/{fazendaId}/alertas/{alertaId}/status",
                "/api/v1/fazendas/{fazendaId}/recomendacoes-irrigacao",
                "/api/v1/fazendas/{fazendaId}/recomendacoes-irrigacao/calcular")) {
            assertTrue(caminhos.containsKey(rota), "falta a rota " + rota);
        }
        assertFalse(caminhos.containsKey("/api/v1/simulador/cenario"), "o simulador desligado não aparece no contrato");
    }

    @Test
    void dtosDeEntradaTrazemDescricaoEExemploEstavel() {
        Map<String, Object> esquemas = api.read("$.components.schemas");
        List<String> semExemplo = new ArrayList<>();
        esquemas.forEach((nome, esquema) -> {
            if (nome.endsWith("Request") && ((Map<String, Object>) esquema).get("example") == null) {
                semExemplo.add(nome);
            }
        });
        assertEquals(List.of(), semExemplo, "requests sem exemplo");

        assertEquals(25.0, ((Number) api.read("$.components.schemas.RegraAlertaRequest.example.limiar")).doubleValue());
        assertEquals("EM_ATENDIMENTO", api.read("$.components.schemas.AlterarStatusAlertaRequest.example.status"));
        assertEquals("Polygon", api.read("$.components.schemas.TalhaoRequest.example.geometria.type"));
        assertEquals("IRRIGAR", api.read("$.components.schemas.RecomendacaoIrrigacaoResponse.example.situacao"));
        assertEquals("SECA", api.read("$.components.schemas.AlertaResponse.example.evento"));
        assertNotNull(api.read("$.components.schemas.AlertaResponse.description"));
        List<String> estados = api.read("$.components.schemas.AlterarStatusAlertaRequest.properties.status.enum");
        assertTrue(estados.containsAll(List.of("ABERTO", "EM_ATENDIMENTO", "RESOLVIDO", "DISPENSADO")));
    }
}
