# Smart Farming Platform

API REST autenticada (JWT) para gestão de usuários, fazendas, talhões, culturas e plantios, construída com Java 21,
Spring Boot, PostgreSQL, JPA e Flyway. A descrição original do projeto está em
[`docs/descricao-projeto.md`](docs/descricao-projeto.md).

## Executar

Configure `DB_USER`, `DB_PASSWORD`, `DB_NAME` e `JWT_SECRET` (mínimo de 32
caracteres) no ambiente. Para o primeiro acesso, defina também `ADMIN_EMAIL` e
`ADMIN_PASSWORD`: o administrador inicial é criado na inicialização se ainda não
existir nenhum. Suba o banco e a aplicação:

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Antes de executar a aplicação, confirme que `DB_USER`, `DB_PASSWORD` e
`DB_NAME` estão exportadas no ambiente do processo Maven. O Maven Wrapper do
repositório está sem `.mvn/wrapper/maven-wrapper.properties`; use Maven instalado
localmente até esse arquivo ser incluído.

Para executar os testes unitários sem iniciar o banco:

```bash
mvn -Dtest=UsuarioServiceTest,FazendaServiceTest,TalhaoServiceTest test
```

## Endpoints

As respostas usam DTOs; o hash da senha nunca é retornado. Todas as rotas usam o
prefixo `/api/v1` e, exceto `/auth/**` e a documentação Swagger, exigem
`Authorization: Bearer <accessToken>` (access token de 15 minutos; refresh token
de 7 dias, com rotação). Há dois perfis: `ADMINISTRADOR` (gerencia usuários e
acessa tudo) e `AGRICULTOR` (acessa só o próprio usuário e suas fazendas). Usuários
não são excluídos, apenas desativados; contas desativadas não acessam o sistema
(`403` com `codigo: USUARIO_DESATIVADO`). O guia de utilização detalha os fluxos.

| Método | Rota | Finalidade |
|---|---|---|
| `POST` | `/auth/registro` | Cadastro público (perfil `AGRICULTOR`) |
| `POST` | `/auth/login` | Login: access e refresh token |
| `POST` | `/auth/refresh` | Renovar tokens |
| `POST` | `/auth/logout` | Revogar refresh token |
| `POST` | `/usuarios` | Criar usuário com perfil à escolha (administrador) |
| `GET` | `/usuarios` | Listar usuários (administrador) |
| `GET` | `/usuarios/{id}` | Consultar usuário (administrador ou o próprio) |
| `PUT` | `/usuarios/{id}` | Atualizar nome e e-mail; perfil só por administrador |
| `PATCH` | `/usuarios/{id}/senha` | Alterar a própria senha informando a senha atual |
| `PATCH` | `/usuarios/me/desativar` | Desativar a própria conta (não vale para administradores) |
| `PATCH` | `/usuarios/{id}/desativar` | Desativar usuário (administrador) |
| `PATCH` | `/usuarios/{id}/ativar` | Reativar usuário (administrador) |
| `POST` | `/usuarios/{usuarioId}/fazendas` | Criar fazenda para usuário |
| `GET` | `/usuarios/{usuarioId}/fazendas` | Listar fazendas do usuário |
| `GET` | `/fazendas` | Listar fazendas (administrador: todas; agricultor: as suas) |
| `GET` | `/fazendas/{id}` | Consultar fazenda |
| `PUT` | `/fazendas/{id}` | Atualizar fazenda |
| `DELETE` | `/fazendas/{id}` | Excluir fazenda sem talhões ou previsões vinculados |
| `POST` | `/fazendas/{fazendaId}/talhoes` | Criar talhão |
| `GET` | `/fazendas/{fazendaId}/talhoes` | Listar talhões da fazenda |
| `GET` | `/fazendas/{fazendaId}/talhoes/{talhaoId}` | Consultar talhão |
| `PUT` | `/fazendas/{fazendaId}/talhoes/{talhaoId}` | Atualizar talhão |
| `DELETE` | `/fazendas/{fazendaId}/talhoes/{talhaoId}` | Excluir talhão sem dispositivos vinculados |
| `GET` | `/culturas`, `/culturas/{id}` | Consultar o catálogo de culturas |
| `POST` `PUT` `DELETE` | `/culturas`, `/culturas/{id}` | Gerenciar culturas (administrador) |
| `POST` `GET` | `/fazendas/{fazendaId}/talhoes/{talhaoId}/plantios` | Criar e listar plantios do talhão |
| `GET` `PUT` `DELETE` | `.../plantios/{plantioId}` | Consultar, atualizar e excluir plantio `PLANEJADO` |
| `PATCH` | `.../plantios/{plantioId}/status` | `PLANEJADO → EM_CULTIVO → COLHIDO` (ou `CANCELADO`); ao colher, data real e quantidade |
| `PATCH` | `.../plantios/{plantioId}/colheita` | Corrigir data e quantidade colhida |

Exemplo de criação de usuário:

```json
{
  "nome": "Ana Silva",
  "email": "ana@example.com",
  "senha": "senha-segura",
  "perfil": "AGRICULTOR"
}
```

Exemplo de atualização de usuário (sem senha):

```json
{
  "nome": "Ana Silva",
  "email": "ana@example.com",
  "perfil": "AGRICULTOR"
}
```

Exemplo de alteração de senha:

```json
{
  "senhaAtual": "senha-segura",
  "novaSenha": "outra-senha-segura"
}
```

Exemplo de criação de fazenda:

```json
{
  "nome": "Fazenda Horizonte",
  "localizacao": "Goiás",
  "areaTotal": 1200.5
}
```

Erros de validação retornam HTTP 400, falta de autenticação HTTP 401, falta de
permissão HTTP 403, recursos inexistentes HTTP 404 e conflitos HTTP 409, com uma
resposta JSON estruturada.
