# 🐟 HookFishing

API REST em **Java + Spring Boot** para gerenciamento de um aquário: peixes, espécies, aquários, cuidadores e alimentações.

| Parte | Conteúdo |
|---|---|
| **Parte 1** | Spring Boot, Maven, H2, Spring Data JPA, 5 entidades com One-to-One / One-to-Many / Many-to-Many, Bean Validation, enum, CRUD completo, paginação com `Pageable`, consultas personalizadas, códigos HTTP, Swagger e HATEOAS |
| **Parte 2** | Idempotência (`X-Idempotency-Key`), autenticação por API Key (`X-API-Key`) com papéis da equipe do aquário, rate limiting (429 + `Retry-After`), CORS, versionamento por header (`X-API-Version`) e tratamento global de erros com `@ControllerAdvice` |

---

## ▶️ Como executar

Pré-requisitos: **Java 25** e Maven (ou o wrapper `mvnw` incluído).

```bash
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

| O quê | Endereço |
|---|---|
| **Front-end** | http://localhost:8080/ |
| Swagger (tema laranja/azul, seletor de versão e login) | http://localhost:8080/swagger-ui/index.html (ou http://localhost:8080/swagger-ui.html, que redireciona) |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs/v1 e http://localhost:8080/v3/api-docs/v2 |
| Console H2 | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:fishdb`, usuário `sa`, senha em branco) |

### Testando no Swagger

1. Escolha a versão da API no seletor do topo (**v1** ou **v2**). Em cada versão, o header `X-API-Version` já vem preenchido.
2. Clique em **Authorize** e entre com **e-mail e senha** (ex.: `admin@hookfishing.com` / `admin123`). Se preferir, informe diretamente uma API Key, como `hf_admin_demo_2025`.
3. Abra uma seção, clique em **Try it out** e depois em **Execute**: os campos já vêm preenchidos com dados reais da base.

O Swagger abre no endereço padrão (`/swagger-ui/index.html`; `/swagger-ui.html` redireciona para lá). A página é `src/main/resources/swagger/index.html`, entregue pelo `SwaggerUiFilter` no lugar da página padrão do springdoc. Ela usa os arquivos do Swagger UI do próprio springdoc (com a CDN como reserva) e o tema laranja e azul de `static/css/swagger-tema.css`.

---

## 🖥️ Front-end

Página em HTML, CSS e JavaScript puro, sem dependências, em `src/main/resources/static`. A própria API serve o front-end em **http://localhost:8080/**.

| Recurso | Como aparece no front-end |
|---|---|
| **Login e cadastro** | Tela de login com e-mail e senha (`POST /auth/login`) e tela "Criar conta" (`POST /auth/cadastro`, com `X-Idempotency-Key`). O login devolve a API Key do usuário, enviada depois no header `X-API-Key` |
| **Papéis da equipe** | Os botões de cadastrar, editar e excluir aparecem por recurso, conforme as permissões do papel (vindas de `GET /api-keys/me`). Ex.: o CUIDADOR vê "Novo" e "Editar" em Peixes, mas não "Excluir". A aba API Keys só aparece para ADMIN |
| **CRUD** das 5 entidades | Uma aba por entidade: tabela paginada, ordenação, tamanho da página, busca personalizada e formulário de cadastro/edição com os relacionamentos (selects e checkboxes) |
| **Idempotência** | Cada formulário de cadastro gera uma `X-Idempotency-Key` (exibida no rodapé). Clicar em Salvar de novo reenvia a mesma chave, e a API não duplica o registro |
| **Rate limit** | Barra no topo com `X-RateLimit-Remaining / X-RateLimit-Limit`. Ao receber 429, aparece um aviso com contagem regressiva do `Retry-After` |
| **Versionamento** | Botões **v1 / v2** na aba Peixes, que enviam o header `X-API-Version` |
| **Erros** | Mensagens do `ErroResponse` em avisos, com os campos inválidos destacados no formulário |
| **API Keys** | Gerar (exibe o valor uma única vez, com botão de copiar), editar papel e status, regenerar e revogar |
| **Testes da Parte 2** | Tabela de cenários por requisito (4.1 a 5) com requisição, status esperado e resultado (Passou/Falhou), detalhes da resposta e botão "Executar todos" |
| **Painel de requisições** | Lateral com cada chamada feita: método, rota, status e os headers da Parte 2 enviados e recebidos |

**CORS na prática:** o front-end também funciona aberto em outra origem permitida. Por exemplo, abrindo `index.html` pelo Live Server do VS Code (porta 5500), ele chama a API em `http://localhost:8080` e o CORS libera a origem. Em uma porta fora da lista, o navegador bloqueia as chamadas.

---

## 🐠 Dados carregados ao iniciar

O banco é em memória. A classe `DataLoader` cadastra os dados abaixo sempre que a aplicação sobe, e todos os exemplos do Swagger usam esses registros.

| Recurso | Registros (id) |
|---|---|
| Espécies | 1 Peixe-palhaço (Pomacentridae) · 2 Cirurgião-patela (Acanthuridae) · 3 Betta (Osphronemidae) · 4 Tetra-neon (Characidae) |
| Cuidadores | 1 João Silva · 2 Ana Souza · 3 Carlos Lima (sem aquário) |
| Aquários | 1 Aquário Principal, 200 L (João) · 2 Aquário Recifal, 350 L (Ana) · 3 Aquário Comunitário, 120 L (sem cuidador) |
| Alimentações | 1 Ração Tropical (RACAO) · 2 Artêmia Viva (VIVA) · 3 Spirulina em Flocos (VEGETAL) · 4 Larva de Mosquito Congelada (CONGELADA) |
| Peixes | 1 Nemo · 2 Marlin · 3 Dory · 4 Azulão · 5 Faísca |
| API Keys (para o Swagger) | 1 `hf_admin_demo_2025` (ADMIN) · 2 `hf_gerente_demo_2025` (GERENTE) · 3 `hf_biologo_demo_2025` (BIOLOGO) · 4 `hf_cuidador_demo_2025` (CUIDADOR) · 5 `hf_visitante_demo_2025` (VISITANTE) |
| Usuários (para o front-end) | `admin@hookfishing.com` / `admin123` (ADMIN) · `gerente@hookfishing.com` / `gerente123` (GERENTE) · `biologo@hookfishing.com` / `biologo123` (BIOLOGO) · `cuidador@hookfishing.com` / `cuidador123` (CUIDADOR) · `visitante@hookfishing.com` / `visitante123` (VISITANTE) |

---

## 🧩 Entidades e relacionamentos

```text
Especie  1 ──── N  Peixe        (One-to-Many)
Aquario  1 ──── N  Peixe        (One-to-Many)
Aquario  1 ──── 1  Cuidador     (One-to-One)
Peixe    N ──── N  Alimentacao  (Many-to-Many, tabela peixe_alimentacao)
```

Enum: `TipoAlimentacao` → `RACAO`, `VIVA`, `VEGETAL`, `CONGELADA`.

Regras de negócio:

- Nome da espécie, nome do aquário, nome da alimentação e e-mail do cuidador são únicos (senão **409**).
- Cada cuidador cuida de no máximo um aquário (senão **409**).
- Não é possível excluir uma espécie com peixes, um aquário com peixes nem um cuidador responsável por um aquário (**409**).
- Ao excluir uma alimentação, ela é retirada da dieta dos peixes (Many-to-Many).

---

## 📁 Estrutura

```text
src/main/java/com/fishapi
├── config/          OpenApiConfig (Swagger), ExemplosOpenApi, WebConfig (CORS + interceptors), DataLoader
├── controller/      Peixe, Especie, Aquario, Cuidador, Alimentacao, ApiKey + Hateoas (paginação com links)
├── dto/             Records de entrada (Request) e saída (Response), com validações e exemplos do Swagger
├── exception/       GlobalExceptionHandler (@RestControllerAdvice), ErroResponse e exceções da API
├── idempotencia/    IdempotenciaService (X-Idempotency-Key)
├── model/           Entidades JPA + enums TipoAlimentacao e NivelAcesso
├── ratelimit/       RateLimiter e RateLimitInterceptor
├── repository/      Repositórios Spring Data JPA com as consultas personalizadas
├── seguranca/       ApiKeyService e ApiKeyInterceptor (X-API-Key)
├── service/         Regras de negócio de cada entidade
└── versionamento/   ApiVersionInterceptor (X-API-Version)

src/main/resources/static   Front-end (index.html, css/app.css, js/app.js)
```

---

## 🌐 Endpoints

Todas as entidades têm 6 endpoints: listar (paginado), buscar por ID, consulta personalizada, cadastrar, atualizar e excluir.

| Entidade | Listar / Criar | Por ID (GET / PUT / DELETE) | Consulta personalizada |
|---|---|---|---|
| Peixe | `/peixes` | `/peixes/{id}` | `GET /peixes/buscar?nome=nemo` |
| Espécie | `/especies` | `/especies/{id}` | `GET /especies/buscar?familia=Pomacentridae` |
| Aquário | `/aquarios` | `/aquarios/{id}` | `GET /aquarios/buscar?capacidadeMinima=200` |
| Cuidador | `/cuidadores` | `/cuidadores/{id}` | `GET /cuidadores/buscar?email=joao@email.com` |
| Alimentação | `/alimentacoes` | `/alimentacoes/{id}` | `GET /alimentacoes/buscar?tipo=RACAO` |

Autenticação (rotas públicas):

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/cadastro` | Cria uma conta (papel `VISITANTE`). Exige `X-Idempotency-Key` |
| POST | `/auth/login` | Entra com e-mail e senha e recebe a API Key do usuário |

Gerenciamento de API Keys (apenas ADMIN, exceto `/me`):

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api-keys` | Lista as chaves (apenas o prefixo, nunca o valor completo) |
| GET | `/api-keys/me` | Dados, papel e permissões da chave em uso (qualquer papel) |
| GET | `/api-keys/{id}` | Busca uma chave |
| POST | `/api-keys` | Gera uma nova chave (o valor aparece apenas nesta resposta) |
| PUT | `/api-keys/{id}` | Altera dono, papel ou ativa/desativa |
| POST | `/api-keys/{id}/regenerar` | Gera um novo valor; o anterior para de funcionar |
| DELETE | `/api-keys/{id}` | Revoga a chave |

Paginação: `?page=0&size=10&sort=nome,asc` (`size` máximo 50). Campo de ordenação inválido → **400**.

### Exemplo de cadastro

```http
POST /peixes
X-API-Key: hf_cuidador_demo_2025
X-Idempotency-Key: 3f2b8c1e-7d4a-4f6b-9c2e-1a5d8e7f9b30
Content-Type: application/json

{
  "nome": "Coral",
  "idade": 1,
  "tamanho": 7.0,
  "especieId": 1,
  "aquarioId": 1,
  "alimentacaoIds": [1, 2]
}
```

---

## 🔐 Parte 2 — Recursos avançados

### 4.1 Idempotência — `X-Idempotency-Key`

Todo **POST de cadastro** (inclusive `POST /auth/cadastro`) exige o header `X-Idempotency-Key` (8 a 100 caracteres, por exemplo um UUID). As chaves usadas ficam na tabela `registro_idempotencia`, separadas por API Key, por 24 horas. Ficam de fora o login e a regeneração de chave, que não criam recursos.

| Situação | Resultado |
|---|---|
| Primeira requisição com a chave | Cria o recurso → **201** + `X-Idempotent-Replayed: false` |
| Mesma chave + mesmo corpo | **Não cria de novo**; devolve o mesmo recurso → **201** + `X-Idempotent-Replayed: true` |
| Mesma chave + corpo diferente | **422** |
| Mesma chave ainda em processamento | **409** |
| Header ausente ou inválido | **400** |
| A criação falhou (ex.: 400, 404 ou 409) | A chave é liberada para nova tentativa |

### 4.2 Autenticação com chave de API — `X-API-Key`

- Todas as rotas da API exigem `X-API-Key`. Ficam livres: `/auth/cadastro`, `/auth/login`, o front-end, o Swagger, `/v3/api-docs` e o H2 Console.
- **Usuários:** `POST /auth/cadastro` cria a conta (senha salva com hash PBKDF2) e uma API Key própria com o papel `VISITANTE`. `POST /auth/login` confere e-mail e senha e devolve um novo valor para essa chave; cada login invalida o anterior. Um `ADMIN` promove o usuário alterando o papel da chave em `PUT /api-keys/{id}`. Chaves de usuários não podem ser excluídas, apenas desativadas.
- Cada chave tem um **papel** inspirado na equipe de um aquário, com permissões diferentes em cada recurso:

| Papel | Quem é | Peixes | Alimentações | Espécies | Aquários | Cuidadores | API Keys |
|---|---|---|---|---|---|---|---|
| `VISITANTE` | Público do aquário | ver | ver | ver | ver | ver | – |
| `CUIDADOR` | Equipe do dia a dia | ver, cadastrar, editar | ver, cadastrar, editar | ver | ver | ver | – |
| `BIOLOGO` | Parte científica | tudo | tudo | tudo | ver | ver | – |
| `GERENTE` | Estrutura do aquário | tudo | tudo | tudo | tudo | tudo | – |
| `ADMIN` | Administrador | tudo | tudo | tudo | tudo | tudo | tudo |

"Tudo" = ver, cadastrar, editar e excluir. A matriz fica em `seguranca/Permissoes.java` e é usada em três lugares: no `ApiKeyInterceptor` (para liberar ou bloquear), no Swagger (cada endpoint mostra os **papéis permitidos** e um exemplo de 403) e em `GET /api-keys/me` (que devolve as permissões para o front-end).

- A chave é gerada com `SecureRandom` e só o hash **SHA-256** é salvo.
- **Login no Swagger:** o botão *Authorize* usa o fluxo *password* do OAuth2. O Swagger envia e-mail e senha para `POST /auth/token`, recebe a API Key do usuário e passa a enviá-la no header `Authorization: Bearer`. A API aceita a chave tanto em `X-API-Key` quanto em `Authorization: Bearer`.
- Chave ausente, inválida ou desativada → **401** (com o header `WWW-Authenticate`).
- Papel sem permissão → **403**, com uma mensagem que diz quais papéis podem. Exemplo: *"Seu papel CUIDADOR não permite excluir peixes. Papéis permitidos: BIOLOGO, GERENTE ou ADMIN."*

### 4.3 Rate limiting

- Algoritmo **Token Bucket**, por cliente (a API Key ou, sem chave, o IP). Cada cliente tem um balde com **60 fichas**; cada requisição consome uma, e o balde é reabastecido com **1 ficha por segundo** até ficar cheio de novo.
- Na prática: rajadas de até 60 requisições seguidas e, em média, 60 por minuto. Os valores ficam no `application.properties`.
- Implementação em `ratelimit/RateLimiter.java`. Ela usa `ConcurrentHashMap.compute`, para que duas requisições simultâneas não consumam a mesma ficha.
- Toda resposta traz `X-RateLimit-Limit`, `X-RateLimit-Remaining` e `X-RateLimit-Reset`.
- Ao exceder o limite → **429 Too Many Requests** com o header `Retry-After`, que indica os segundos até poder tentar de novo.

Para demonstrar, reduza a capacidade para 5 em `hookfishing.rate-limit.capacidade` e faça 6 requisições seguidas:

```bash
for i in $(seq 1 6); do curl -s -o /dev/null -w "%{http_code}\n" -H "X-API-Key: hf_visitante_demo_2025" http://localhost:8080/peixes; done
```

### 4.4 CORS

Configurado em `WebConfig` a partir da propriedade `hookfishing.cors.origens-permitidas`. Origens padrão:

```text
http://localhost:3000, http://localhost:5173, http://localhost:5500, http://127.0.0.1:5500, http://localhost:8080
```

- Métodos permitidos: `GET`, `POST`, `PUT`, `DELETE` e `OPTIONS`.
- Headers aceitos: `Content-Type`, `Accept`, `X-API-Key`, `X-API-Version` e `X-Idempotency-Key`.
- Headers expostos ao JavaScript: `Location`, `Retry-After`, `X-RateLimit-*`, `X-API-Version`, `X-Idempotency-Key` e `X-Idempotent-Replayed`.

### 4.5 Versionamento — `X-API-Version`

`GET /peixes` e `GET /peixes/{id}` têm duas versões:

| Header | Resposta |
|---|---|
| sem header ou `X-API-Version: 1` | Relacionamentos como IDs: `especieId`, `aquarioId`, `alimentacaoIds` |
| `X-API-Version: 2` | Relacionamentos detalhados (`especie`, `aquario`, `alimentacoes`) e campos calculados (`faixaEtaria`, `tamanhoFormatado`) |
| qualquer outro valor | **400** |

A versão usada volta no header de resposta `X-API-Version`.

```bash
curl -H "X-API-Key: hf_visitante_demo_2025" http://localhost:8080/peixes/1
curl -H "X-API-Key: hf_visitante_demo_2025" -H "X-API-Version: 2" http://localhost:8080/peixes/1
```

### 5. Validações e tratamento de erros

- Bean Validation nos DTOs de entrada (`@NotBlank`, `@Size`, `@NotNull`, `@Min`, `@Positive`, `@Email`).
- `GlobalExceptionHandler` (`@RestControllerAdvice`) converte toda falha para o mesmo formato:

```json
{
  "timestamp": "2026-11-12T22:30:00Z",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Um ou mais campos são inválidos. Corrija os campos listados em 'campos' e envie novamente.",
  "caminho": "/peixes",
  "campos": [ { "campo": "nome", "mensagem": "O nome do peixe é obrigatório" } ]
}
```

| Código | Quando acontece |
|---|---|
| 200 | Consulta ou atualização |
| 201 | Recurso criado (com header `Location`) |
| 204 | Recurso excluído |
| 400 | Validação, JSON inválido, parâmetro, header ou ordenação inválidos, versão inexistente |
| 401 | `X-API-Key` ausente, inválida ou desativada |
| 403 | O papel da chave não permite a operação |
| 404 | Recurso ou relacionamento inexistente, ou rota inexistente |
| 405 | Método não suportado na rota |
| 409 | Duplicidade, recurso ainda vinculado, chave de idempotência em processamento |
| 415 | `Content-Type` diferente de `application/json` |
| 422 | Chave de idempotência reutilizada com outro corpo |
| 429 | Limite de requisições excedido |
| 500 | Erro inesperado |

### 6. Documentação

O Swagger (tema laranja e azul, com seletor entre v1 e v2) tem uma descrição geral explicando autenticação, idempotência, rate limit, versionamento, CORS, paginação, dados iniciais e códigos HTTP. Além disso:

- Cada aba tem uma descrição com exemplos do que dá para fazer nela.
- Cada endpoint documenta todos os códigos de resposta possíveis, com exemplos de erro.
- Todos os campos e parâmetros usam exemplos com os dados reais da base, nada de `"string"` genérico.

---

## ⚙️ Configurações (`application.properties`)

| Propriedade | Padrão | Uso |
|---|---|---|
| `hookfishing.api-keys.admin` / `.gerente` / `.biologo` / `.cuidador` / `.visitante` | `hf_*_demo_2025` | Chaves de demonstração criadas ao iniciar (uma por papel) |
| `hookfishing.rate-limit.capacidade` | `60` | Fichas do balde (rajada máxima de requisições) |
| `hookfishing.rate-limit.recarga-por-segundo` | `1` | Fichas devolvidas ao balde por segundo |
| `hookfishing.idempotencia.validade-horas` | `24` | Validade das chaves de idempotência |
| `hookfishing.cors.origens-permitidas` | ver acima | Origens liberadas no CORS |

---

## Termos de serviço

Ao usar a HookFishing API, você concorda com as regras abaixo.

- **Use a API para o fim a que ela se destina:** gerenciar peixes, espécies, aquários, cuidadores e alimentações (ou estudar e testar o projeto).
- **Envie dados verdadeiros e válidos**, em JSON e UTF-8, respeitando as regras de validação.
- **Respeite a privacidade dos dados pessoais** de usuários e cuidadores (nome e e-mail), conforme a LGPD (Lei nº 13.709/2018).
- **Não compartilhe sua API Key** e não tente burlar o rate limit, as permissões ou a autenticação. O descumprimento pode levar à desativação da chave.
- **Exclusões não podem ser desfeitas.**
- **Banco em memória:** os dados são apagados sempre que a aplicação reinicia.
- A API é fornecida para fins acadêmicos, sem garantia de disponibilidade.

---

## Licença

Este projeto está sob a **Licença MIT** (veja o arquivo [`LICENSE`](LICENSE)).

Em resumo, qualquer pessoa pode usar, copiar, modificar e distribuir o código, inclusive em projetos comerciais, desde que mantenha o aviso de copyright e o texto da licença. O software é fornecido **sem garantia**.

---

Projeto acadêmico desenvolvido para a disciplina de Desenvolvimento de APIs com Spring Boot.
