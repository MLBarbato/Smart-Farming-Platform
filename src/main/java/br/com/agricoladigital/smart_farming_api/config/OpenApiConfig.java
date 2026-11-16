package br.com.agricoladigital.smart_farming_api.config;

import br.com.agricoladigital.smart_farming_api.dto.response.ErroResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;

import java.util.Map;

/**
 * Documentação OpenAPI: descrição geral, autenticação JWT e respostas de erro padronizadas. Os resumos e
 * exemplos ficam nas anotações dos controllers e dos DTOs; aqui só entra o que vale para a API inteira.
 */
@Configuration
public class OpenApiConfig {
    private static final String ESQUEMA = "bearerAuth";
    private static final String ERRO = "ErroResponse";

    static final String DESCRICAO = """
            API REST da plataforma de agricultura de precisão (backend). O frontend é desenvolvido à parte.

            **Autenticação.** Faça login em `POST /api/v1/auth/login` e envie o `accessToken` (JWT, 15 min) em
            `Authorization: Bearer <token>`. Renove com `POST /api/v1/auth/refresh`: o refresh token (7 dias) é
            rotativo, então guarde o novo a cada renovação. O botão *Authorize* desta página usa o accessToken.

            **Perfis.** `AGRICULTOR` acessa só as próprias fazendas e o que pertence a elas; `ADMINISTRADOR` acessa
            tudo e mantém o catálogo de culturas, as regras de alerta e os usuários (que nunca são excluídos, só
            desativados). Usuário desativado recebe `403` com `codigo: USUARIO_DESATIVADO`.

            **Convenções.** Instantes em UTC (`Z`); datas de calendário `AAAA-MM-DD`. Listas grandes são paginadas
            (`pagina` a partir de 0, `tamanho` de 1 a 100) com `{conteudo, pagina, tamanho, totalElementos,
            totalPaginas}`. Erros têm sempre o formato `ErroResponse`: `400` validação, `401` sem token válido,
            `403` sem permissão, `404` inexistente (ou de outra fazenda), `409` conflito de estado ou vínculos,
            `429` login bloqueado, `503` provedor externo indisponível.

            **Dados externos.** Previsão e estação virtual vêm do Open-Meteo (uso não comercial, licença CC BY 4.0):
            exiba o campo `atribuicao` devolvido. Quando a previsão está `DESATUALIZADA`, avise o usuário.

            **Aviso.** Regras de alerta e parâmetros de irrigação são **valores de demonstração**, não validados por
            agrônomo: os alertas e as recomendações apoiam a decisão, não a substituem.
            """;

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Smart Farming API").version("v1").description(DESCRICAO))
                .addServersItem(new Server().url("/").description("Servidor atual"))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }

    /** Registra o formato de erro nos componentes e o liga a todas as respostas 4xx/5xx. */
    @Bean
    OpenApiCustomizer respostasDeErro() {
        return openApi -> {
            Map<String, Schema> esquemas = ModelConverters.getInstance()
                    .readAll(new AnnotatedType(ErroResponse.class));
            Components componentes = openApi.getComponents();
            esquemas.forEach(componentes::addSchemas);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(item -> item.readOperations().forEach(operacao -> {
                if (operacao.getResponses() == null) {
                    return;
                }
                operacao.getResponses().forEach((codigo, resposta) -> {
                    if (codigo.startsWith("4") || codigo.startsWith("5")) {
                        resposta.setContent(conteudoDeErro());
                    }
                });
            }));
        };
    }

    /**
     * Acrescenta as respostas de erro que decorrem da própria declaração do método: 401 e 403 nas rotas
     * protegidas, 400 quando há corpo ou parâmetros, 404 quando há identificador no caminho. As demais
     * (409, 429, 503...) são declaradas método a método.
     */
    @Bean
    OperationCustomizer errosPadrao() {
        return (Operation operacao, org.springframework.web.method.HandlerMethod metodo) -> {
            ApiResponses respostas = operacao.getResponses() == null ? new ApiResponses() : operacao.getResponses();
            operacao.setResponses(respostas);
            // O springdoc só enxerga ResponseEntity<T> e documentaria 200: criações respondem 201 e as
            // operações sem corpo (ResponseEntity<Void>) respondem 204.
            org.springframework.core.ResolvableType retorno =
                    org.springframework.core.ResolvableType.forMethodReturnType(metodo.getMethod());
            if (org.springframework.http.ResponseEntity.class.equals(retorno.resolve()) && respostas.containsKey("200")) {
                boolean semCorpo = Void.class.equals(retorno.getGeneric(0).resolve());
                boolean criacao = !semCorpo
                        && metodo.hasMethodAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
                if (semCorpo || criacao) {
                    ApiResponse sucesso = respostas.remove("200");
                    sucesso.setDescription(semCorpo ? "Sem conteúdo" : "Criado");
                    if (semCorpo) {
                        sucesso.setContent(null);
                    }
                    ApiResponses reordenadas = new ApiResponses();
                    reordenadas.addApiResponse(semCorpo ? "204" : "201", sucesso);
                    respostas.forEach(reordenadas::addApiResponse);
                    operacao.setResponses(reordenadas);
                    respostas = reordenadas;
                }
            }
            for (Erro erro : metodo.getMethod().getAnnotationsByType(Erro.class)) {
                adicionar(respostas, erro.codigo(), erro.descricao());
            }
            boolean protegida = operacao.getSecurity() == null || !operacao.getSecurity().isEmpty();
            boolean publica = metodo.getBeanType().getName().endsWith("AuthController");
            if (protegida && !publica) {
                adicionar(respostas, "401", "Sem token válido");
                boolean restrita = metodo.hasMethodAnnotation(PreAuthorize.class)
                        || metodo.getBeanType().isAnnotationPresent(PreAuthorize.class);
                if (restrita) {
                    adicionar(respostas, "403", "Sem permissão para este recurso");
                }
            }
            boolean temEntrada = metodo.getMethodParameters().length > 0 && java.util.Arrays.stream(metodo.getMethodParameters())
                    .anyMatch(p -> p.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)
                            || p.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestParam.class));
            if (temEntrada) {
                adicionar(respostas, "400", "Dados ou filtros inválidos");
            }
            boolean temIdentificador = java.util.Arrays.stream(metodo.getMethodParameters())
                    .anyMatch(p -> p.hasParameterAnnotation(org.springframework.web.bind.annotation.PathVariable.class));
            if (temIdentificador && !publica) {
                adicionar(respostas, "404", "Recurso não encontrado");
            }
            descreverParametros(operacao);
            return operacao;
        };
    }

    private static final Map<String, String> PARAMETROS = Map.ofEntries(
            Map.entry("id", "Identificador do recurso"),
            Map.entry("fazendaId", "Identificador da fazenda"),
            Map.entry("usuarioId", "Identificador do usuário"),
            Map.entry("talhaoId", "Identificador do talhão (como filtro, restringe a um talhão da fazenda)"),
            Map.entry("plantioId", "Identificador do plantio"),
            Map.entry("sensorId", "Identificador do sensor"),
            Map.entry("alertaId", "Identificador do alerta"),
            Map.entry("recomendacaoId", "Identificador da recomendação de irrigação"),
            Map.entry("status", "Filtro por estado; em alertas aceita vários valores separados por vírgula"),
            Map.entry("evento", "Filtro por evento: SECA, EXCESSO_PRECIPITACAO, RISCO_GEADA ou FALHA_SENSOR"),
            Map.entry("criticidade", "Filtro por criticidade: BAIXA, MEDIA ou ALTA"),
            Map.entry("situacao", "Filtro por situação do resultado"),
            Map.entry("ativa", "Filtro por regra ativa ou inativa"),
            Map.entry("culturaId", "Filtro por cultura do catálogo"),
            Map.entry("safra", "Safra no formato AAAA/AAAA, por exemplo 2026/2027"),
            Map.entry("variavel", "Variável monitorada, por exemplo UMIDADE_SOLO ou TEMPERATURA_AR"),
            Map.entry("de", "Início do período (ISO-8601 com fuso, por exemplo 2026-11-01T00:00:00Z)"),
            Map.entry("ate", "Fim do período (ISO-8601 com fuso)"),
            Map.entry("intervalo", "Intervalo de agregação: HORA (padrão) ou DIA"),
            Map.entry("fuso", "Fuso IANA que delimita os intervalos, por exemplo America/Sao_Paulo (padrão UTC)"),
            Map.entry("limite", "Máximo de leituras devolvidas (1 a 5000; padrão 500)"),
            Map.entry("pagina", "Número da página, a partir de 0 (padrão 0)"),
            Map.entry("tamanho", "Itens por página, de 1 a 100 (padrão 20)")
    );

    private static void descreverParametros(Operation operacao) {
        if (operacao.getParameters() == null) {
            return;
        }
        operacao.getParameters().forEach(parametro -> {
            if (parametro.getDescription() == null) {
                parametro.setDescription(PARAMETROS.get(parametro.getName()));
            }
        });
    }

    private static void adicionar(ApiResponses respostas, String codigo, String descricao) {
        if (!respostas.containsKey(codigo)) {
            respostas.addApiResponse(codigo, new ApiResponse().description(descricao));
        }
    }

    private static Content conteudoDeErro() {
        return new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERRO)));
    }
}
