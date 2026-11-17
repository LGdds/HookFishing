# 🐟 HookFishing

API REST em **Java + Spring Boot** para gerenciamento de um aquário: peixes, espécies, aquários, cuidadores e alimentações.

| Parte | Conteúdo |
|---|---|
| **Parte 1** | Spring Boot, Maven, H2, Spring Data JPA, 5 entidades com One-to-One / One-to-Many / Many-to-Many, Bean Validation, enum, CRUD completo, paginação com `Pageable`, consultas personalizadas, códigos HTTP, Swagger e HATEOAS |
| **Parte 2** | Idempotência (`X-Idempotency-Key`), autenticação por API Key (`X-API-Key`) com papéis da equipe do aquário, rate limiting por plano (429 + `Retry-After` e bloqueio progressivo de quem insiste), planos de assinatura (monetização), CORS, versionamento por header (`X-API-Version`) e tratamento global de erros com `@ControllerAdvice` |

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

### Rodando com Docker

```bash
docker build -t hookfishing .
docker run -p 8080:8080 hookfishing
```

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
| **Rate limit** | Barra no topo com `X-RateLimit-Remaining / X-RateLimit-Limit` e o plano da conta. Ao receber 429, aparece um aviso com contagem regressiva do `Retry-After`; se a chave for bloqueada por insistir, o aviso fica vermelho e mostra o horário de liberação (`X-RateLimit-Blocked-Until`) |
| **Minha conta** | Clique no seu nome no topo: dados da conta, a sua API Key (mostrar, copiar e gerar uma nova com `POST /api-keys/me/regenerar`), exemplo de uso, consumo do plano e a matriz de permissões do seu papel |
| **Planos** | Aba com os três planos e preços (`GET /planos`), o consumo atual da chave (`GET /planos/limites`), as regras de bloqueio e o botão de contratar (`PUT /planos/assinatura`, simulação) |
| **Versionamento** | Botões **v1 / v2** na aba Peixes, que enviam o header `X-API-Version` |
| **Erros** | Mensagens do `ErroResponse` em avisos, com os campos inválidos destacados no formulário |
| **API Keys** | Gerar (exibe o valor uma única vez, com botão de copiar), editar papel e status, regenerar e revogar |
| **Testes** | Tabela de cenários por requisito (4.1 a 5), incluindo planos, estouro do limite e bloqueio, com requisição, status esperado e resultado (Passou/Falhou), detalhes da resposta e botão "Executar todos" |
| **Painel de requisições** | Lateral com cada chamada feita: método, rota, status e os headers da Parte 2 enviados e recebidos |

**CORS na prática:** o front-end também funciona aberto em outra origem permitida. Por exemplo, abrindo `index.html` pelo Live Server do VS Code (porta 5500), ele chama a API em `http://localhost:8080` e o CORS libera a origem. Em uma porta fora da lista, o navegador bloqueia as chamadas.

**Endereço da API:** servido pela própria API (localhost:8080 ou Render), o front-end usa o mesmo endereço da página. Aberto direto no navegador (duplo clique em `src/main/resources/static/index.html`), pelo IntelliJ ou pelo Live Server, a tela de login mostra o bloco **Servidor da API** para escolher entre **Local** (`http://localhost:8080`), **Render** (`https://hookfishing.onrender.com`) ou outro endereço; a escolha fica salva no navegador. O CORS libera essas origens, incluindo `null`, que é a origem enviada pelo navegador para arquivos abertos do disco.

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
├── config/          OpenApiConfig (Swagger), ExemplosOpenApi, WebConfig (CORS + interceptors), FiltroCors, DataLoader
├── controller/      Peixe, Especie, Aquario, Cuidador, Alimentacao, ApiKey, Auth, Plano + Hateoas (paginação com links)
├── dto/             Records de entrada (Request) e saída (Response), com validações e exemplos do Swagger
├── exception/       GlobalExceptionHandler (@RestControllerAdvice), ErroResponse e exceções da API
├── idempotencia/    IdempotenciaService (X-Idempotency-Key)
├── model/           Entidades JPA + enums TipoAlimentacao, PapelAcesso e Plano
├── ratelimit/       RateLimiter (token bucket por plano + bloqueio) e RateLimitInterceptor
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

Planos e limites:

| Método | Rota | Descrição |
|---|---|---|
| GET | `/planos` | Tabela de planos e preços (pública) |
| GET | `/planos/limites` | Plano da chave, fichas restantes e regras de bloqueio |
| PUT | `/planos/assinatura` | Troca o plano da chave em uso (simulação, sem cobrança) |
| POST | `/planos/limites/simulacao?requisicoes=30` | Dispara uma rajada contra o limite da chave (mostra o 429 e o bloqueio pelo Swagger) |
| GET | `/cors?origem=http://localhost:5500` | Política de CORS e verificação de uma origem (pública) |

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
- **Usuários:** `POST /auth/cadastro` cria a conta (senha salva com hash PBKDF2) e uma API Key própria com o papel `VISITANTE`. `POST /auth/login` confere e-mail e senha e devolve a API Key do usuário. Cada login devolve **a mesma chave** (guardada com hash para autenticar e cifrada com AES-GCM para poder ser devolvida), então entrar no Swagger não derruba a sessão do front-end. Para trocar a chave, use `POST /api-keys/me/regenerar` (aba **Minha conta**). Um `ADMIN` promove o usuário alterando o papel da chave em `PUT /api-keys/{id}`. Chaves de usuários não podem ser excluídas, apenas desativadas.
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

### 4.3 Rate limiting, planos e bloqueio

#### Planos (como a API ganha dinheiro)

Modelo **freemium**: toda conta nova começa no plano `GRATUITO`, suficiente para estudar e testar. Quem precisa de mais volume contrata um plano pago, que aumenta o limite de requisições da API Key. A cobrança é por assinatura mensal, por chave.

| Plano | Preço | Requisições de uma vez | Recarga | Para quem |
|---|---|---|---|---|
| Sem chave (por IP, rotas públicas) | – | 10 | 20 por minuto | Login, cadastro e tabela de planos |
| `GRATUITO` | Grátis | 20 | 30 por minuto (1 a cada 2 s) | Estudar e testar |
| `BASICO` | R$ 29,90/mês | 60 | 120 por minuto (2 por segundo) | Aquários pequenos, poucos usuários |
| `PROFISSIONAL` | R$ 99,90/mês | 200 | 600 por minuto (10 por segundo) | Redes de aquários e integrações |

- O plano fica na própria API Key (`model/Plano.java` e coluna `plano` em `api_key`). O ADMIN altera em `PUT /api-keys/{id}`; o próprio usuário troca em `PUT /planos/assinatura`.
- A contratação é uma **simulação acadêmica**: não há cobrança. Em produção, essa etapa passaria por um meio de pagamento (ex.: um checkout com webhook que confirma o pagamento e só então troca o plano).
- Chaves de demonstração: `hf_admin_demo_2025` é PROFISSIONAL; gerente e biólogo, BASICO; cuidador e visitante, GRATUITO.

#### Token bucket

- Cada cliente tem um **balde de fichas** do tamanho do seu plano. Cada requisição consome uma ficha, e o balde é reabastecido aos poucos (recarga por minuto). Dá para fazer uma rajada até o tamanho do balde; depois, só no ritmo da recarga.
- Com API Key, o balde é da chave. Sem chave, é do IP (atrás do proxy do Render, o IP real vem do `X-Forwarded-For`).
- Há também um **limite global** da API inteira (1000 de uma vez, 3000 por minuto), que a protege quando muitos clientes chegam juntos.
- Toda resposta traz `X-RateLimit-Limit` (capacidade), `X-RateLimit-Remaining` (fichas restantes), `X-RateLimit-Reset` (segundos até o balde encher) e `X-RateLimit-Policy` (ex.: `GRATUITO;capacidade=20;recarga=30/min`).

#### O que acontece quando o limite estoura

| Situação | Resposta | Quanto esperar |
|---|---|---|
| Balde vazio | **429** com `Retry-After` e a mensagem *"Próxima requisição liberada em 2 segundo(s). Atenção: mais 4 tentativa(s) antes disso bloqueiam o acesso por 60 segundos."* | O tempo até a próxima ficha (no Gratuito, 2 s) |
| Insistiu: 5 requisições recusadas seguidas | **429** com `Retry-After: 60` e `X-RateLimit-Blocked-Until` (horário de liberação, UTC). O bloqueio vale mesmo que o balde recarregue | **60 segundos** |
| Novo bloqueio em menos de 10 minutos | O tempo **dobra**: 120 s, 240 s, 480 s... | Até no máximo **15 minutos** |
| Esperou o `Retry-After` e a requisição passou | A contagem de tentativas volta a zero | – |

- `GET /planos/limites` mostra o plano, as fichas restantes e essas regras para a chave em uso.
- Implementação em `ratelimit/RateLimiter.java` (baldes e penalidade, com métodos `synchronized` para que duas requisições simultâneas não usem a mesma ficha) e `ratelimit/RateLimitInterceptor.java` (identifica o cliente, escreve os headers e lança o 429).
- Os baldes ficam em memória: zeram ao reiniciar e não são compartilhados entre várias instâncias. Numa produção distribuída, o estado iria para um Redis.

Para ver o 429 e depois o bloqueio com a chave do visitante (plano Gratuito, 20 de uma vez):

```bash
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "%{http_code} retry=%header{retry-after} bloqueio=%header{x-ratelimit-blocked-until}\n" \
    -H "X-API-Key: hf_visitante_demo_2025" http://localhost:8080/especies
done
```

As 20 primeiras respondem 200; as 4 seguintes, 429 com `Retry-After` curto; a partir da 25ª, 429 com `X-RateLimit-Blocked-Until` (bloqueio de 60 s).

### 4.4 CORS

Configurado em `WebConfig` a partir da propriedade `hookfishing.cors.origens-permitidas`. Origens padrão:

```text
http://localhost:3000, http://localhost:5173, http://localhost:5500, http://127.0.0.1:5500, http://localhost:8080, http://localhost:63342
```

- O CORS é um `CorsFilter` com a maior prioridade: ele responde o preflight (`OPTIONS`) antes do rate limit e da autenticação, e também acrescenta os headers CORS nas respostas de erro (401, 403, 429).
- Métodos permitidos: `GET`, `POST`, `PUT`, `DELETE` e `OPTIONS`.
- Headers aceitos: `Content-Type`, `Accept`, `Authorization`, `X-API-Key`, `X-API-Version` e `X-Idempotency-Key`.
- Headers expostos ao JavaScript: `Location`, `Retry-After`, `X-RateLimit-*` (incluindo `Policy` e `Blocked-Until`), `X-API-Version`, `X-Idempotency-Key` e `X-Idempotent-Replayed`.
- Em produção, a lista vem da variável de ambiente `HOOKFISHING_CORS_ORIGINS`, e a URL pública do serviço no Render (`RENDER_EXTERNAL_URL`) entra automaticamente (veja o deploy abaixo).

Testando o preflight:

```bash
curl -i -X OPTIONS http://localhost:8080/peixes \
  -H "Origin: http://localhost:5500" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: X-API-Key, X-Idempotency-Key, Content-Type"
# 200 com Access-Control-Allow-Origin: http://localhost:5500
# Com Origin: http://evil.com -> 403 "Invalid CORS request"
```

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
| 429 | Limite do plano excedido ou chave bloqueada por insistir (com `Retry-After` e, no bloqueio, `X-RateLimit-Blocked-Until`) |
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
| `hookfishing.rate-limit.anonimo.capacidade` / `.recarga-por-minuto` | `10` / `20` | Limite por IP para quem chama sem chave (os limites com chave vêm do plano) |
| `hookfishing.rate-limit.global.capacidade` / `.recarga-por-minuto` | `1000` / `3000` | Limite da API inteira |
| `hookfishing.rate-limit.penalidade.tentativas` | `5` | Requisições recusadas seguidas que disparam o bloqueio |
| `hookfishing.rate-limit.penalidade.segundos` | `60` | Duração do primeiro bloqueio |
| `hookfishing.rate-limit.penalidade.maximo-segundos` | `900` | Bloqueio máximo (15 min) |
| `hookfishing.rate-limit.penalidade.janela-reincidencia-segundos` | `600` | Bloqueio nesse intervalo conta como reincidência e dobra o tempo |
| `hookfishing.idempotencia.validade-horas` | `24` | Validade das chaves de idempotência |
| `hookfishing.cors.origens-permitidas` | ver acima (variável `HOOKFISHING_CORS_ORIGINS`) | Origens liberadas no CORS |
| `server.port` | `8080` (variável `PORT`) | Porta HTTP |

---

## ☁️ Deploy no Render

O projeto já vem com `Dockerfile` e `render.yaml`.

1. Suba o código para o GitHub.
2. No Render, clique em **New > Blueprint** e escolha o repositório (ele lê o `render.yaml`). Ou crie um **Web Service** com *Runtime: Docker*.
3. CORS: a URL pública do serviço é liberada sozinha (o Render define a variável `RENDER_EXTERNAL_URL`, lida em `hookfishing.cors.url-publica`). Se o front-end for rodar em outro endereço (outro site, Live Server), acrescente-o em `HOOKFISHING_CORS_ORIGINS` (lista separada por vírgula, sem barra no final).
4. Opcional: troque as chaves de demonstração com `HOOKFISHING_ADMIN_KEY`, `HOOKFISHING_GERENTE_KEY` etc.
5. Depois do deploy: front-end em `https://SEU-SERVICO.onrender.com/`, Swagger em `/swagger-ui/index.html`.

Detalhes:

- O Render informa a porta na variável `PORT`; a aplicação lê com `server.port=${PORT:8080}`.
- `server.forward-headers-strategy=framework` faz a API respeitar os headers `X-Forwarded-*` do proxy do Render: os links HATEOAS e o Swagger saem com `https://` e o rate limit enxerga o IP real.
- O health check usa `/` (a página do front-end), que não passa pelo rate limit.
- No plano gratuito do Render, o serviço hiberna depois de um tempo sem acesso: a primeira requisição depois disso demora até cerca de um minuto. O banco H2 é em memória, então os dados voltam ao inicial a cada reinício.
- Front-end e Swagger no mesmo serviço da API = mesma origem. O navegador ainda manda o header `Origin` em POST e PUT, e atrás do proxy do Render o Spring enxerga a requisição como `http://host-interno:10000`; o `CorsFilter` padrão achava que era outra origem e respondia **403 "Invalid CORS request"**. O `FiltroCors` (em `config/`) compara a origem com o host que o navegador usou (`X-Forwarded-Host` ou `Host`) e deixa passar as requisições da mesma origem.
- O Swagger anuncia o servidor como `/` (relativo), então o *Try it out* sempre chama o mesmo endereço da página, em `https`. Antes, ele podia chamar `http://...` e o navegador bloqueava ("Failed to fetch... CORS").
- Logo após o serviço acordar, o Render pode responder 502 sem headers de CORS, e o navegador mostra isso como erro de CORS. Espere a primeira carga terminar e tente de novo.
- O CORS só precisa de configuração quando o front-end roda em outro endereço (Live Server, Vite ou outro site); essa origem precisa estar em `HOOKFISHING_CORS_ORIGINS`.

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
