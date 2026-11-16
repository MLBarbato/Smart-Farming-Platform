# Smart Farming Platform

API REST (backend) de uma plataforma de agricultura de precisão: gestão de usuários, fazendas, talhões, culturas
e plantios; sensores e leituras; dados de mapa e resumos para dashboard; previsão do tempo e estação virtual
(Open-Meteo); alertas automáticos com fluxo de atendimento; e recomendações de irrigação. Construída com Java 21,
Spring Boot 4, PostgreSQL, JPA e Flyway. O frontend é desenvolvido por outra equipe e consome esta API. A
descrição original do projeto está em [`docs/descricao-projeto.md`](docs/descricao-projeto.md).

> **Aviso.** As regras de alerta e os parâmetros de irrigação são **valores de demonstração**, não validados por
> agrônomo: alertas e recomendações apoiam a decisão, não a substituem.

## O que a API oferece

| Módulo | Principais recursos |
|---|---|
| Autenticação e usuários | Cadastro, login com JWT (15 min) e refresh token rotativo (7 dias), perfis `ADMINISTRADOR` e `AGRICULTOR`, limite de tentativas de login, usuários desativados nunca excluídos |
| Fazendas, talhões, culturas, plantios | CRUD com propriedade por usuário, geometria GeoJSON dos talhões, ciclo do plantio e produtividade (kg/ha) |
| Sensores e leituras | Sensores por talhão, ingestão validada, leituras atuais e históricas, simulador para demonstração |
| Mapa e resumos | Polígonos, sensores, alertas e recomendações por talhão; indicadores por fazenda e gerais |
| Previsão e estação virtual | Previsão diária e leituras horárias do Open-Meteo, com tratamento de dados desatualizados |
| Alertas | Regras ajustáveis (seca, excesso de chuva, risco de geada, falha de sensor), avaliação a cada 15 min, sem duplicidade, atendimento pelo dono da fazenda |
| Irrigação | Recomendação por talhão (irrigar, aguardar chuva, não irrigar ou dados insuficientes), com justificativa e insumos gravados |

## Executar

Requisitos: Java 21, Maven 3.9+ e Docker. O passo a passo completo está em
[`docs/manual-de-instalacao.md`](docs/manual-de-instalacao.md); em resumo:

```bash
cp .env.example .env                 # edite: senha do banco e JWT_SECRET (mínimo de 32 caracteres)
set -a; source .env; set +a          # o Java não lê o .env sozinho
docker compose up -d postgres        # PostgreSQL 16
mvn spring-boot:run                  # as migrações Flyway rodam na subida
```

Defina também `ADMIN_EMAIL` e `ADMIN_PASSWORD` para criar o administrador inicial. O Maven Wrapper do
repositório está sem `.mvn/wrapper/maven-wrapper.properties`; use o Maven instalado.

- **Swagger UI**: <http://localhost:8080/swagger-ui.html> · **contrato OpenAPI**: <http://localhost:8080/v3/api-docs>
- **Dados de demonstração** (opcional): `DEMO_DADOS_ENABLED=true` e `DEMO_SENHA=...` (e `SIMULADOR_ENABLED=true`
  para cenários ao vivo). Roteiro em [`docs/roteiro-demonstracao.md`](docs/roteiro-demonstracao.md).

## Testes

```bash
mvn test                                                              # unitários + integração (precisa de Docker)
mvn -Dtest='!*IntegracaoTest,!SmartFarmingApiApplicationTests' test   # só unitários
```

Os testes de integração usam um PostgreSQL em contêiner (Testcontainers) com as migrações Flyway; são ignorados
quando não há Docker.

## Documentação

| Documento | Conteúdo |
|---|---|
| [`docs/manual-de-instalacao.md`](docs/manual-de-instalacao.md) | Instalação, variáveis, execução, atualização, logs, segurança, backup e problemas comuns |
| [`docs/arquitetura.md`](docs/arquitetura.md) | Diagramas (arquitetura, modelo de dados, autenticação, jobs, alertas, irrigação) |
| [`docs/guia-de-utilizacao.md`](docs/guia-de-utilizacao.md) | Como usar as rotas, com exemplos |
| [`docs/roteiro-demonstracao.md`](docs/roteiro-demonstracao.md) | Roteiro de apresentação com dados de demonstração |
| Swagger UI / OpenAPI | Contrato completo, exemplos de entrada e saída, respostas de erro |

## Convenções da API

Todas as rotas usam o prefixo `/api/v1` e, exceto `/auth/**` e a documentação, exigem
`Authorization: Bearer <accessToken>`. Instantes em UTC; listas grandes paginadas. As respostas usam DTOs (o hash
da senha nunca é retornado) e os erros têm formato único: `400` validação, `401` sem autenticação, `403` sem
permissão, `404` inexistente, `409` conflito, `429` login bloqueado e `503` provedor externo indisponível.

Dados meteorológicos por [Open-Meteo.com](https://open-meteo.com), licença CC BY 4.0 (uso gratuito apenas não
comercial).
