package com.fishapi.config;

/**
 * Exemplos usados na documentação Swagger.
 * Todos refletem os dados reais carregados pelo DataLoader.
 */
public final class ExemplosOpenApi {

    private ExemplosOpenApi() {
    }

    // ===================== Headers =====================

    public static final String IDEMPOTENCY_KEY = "3f2b8c1e-7d4a-4f6b-9c2e-1a5d8e7f9b30";

    public static final String DESC_IDEMPOTENCY_KEY = """
            Chave única da operação (ex.: um UUID). **Obrigatória em todo POST.**
            Reenviar a mesma chave com o mesmo corpo **não cria um novo registro**: a API devolve o recurso \
            criado na primeira vez, com o header `X-Idempotent-Replayed: true`.
            Mesma chave com outro corpo → **422**. Validade: 24 horas. Para um novo cadastro, gere uma nova chave.""";

    public static final String DESC_VERSAO = """
            Versão da API. `1` (padrão, usada quando o header não é enviado) ou `2`.
            Na **v1** os relacionamentos vêm só como IDs; na **v2** vêm detalhados (espécie, aquário, \
            alimentações) e com campos calculados (`faixaEtaria`, `tamanhoFormatado`).""";

    // ===================== Paginação =====================

    public static final String DESC_PAGE = "Número da página (começa em 0).";
    public static final String DESC_SIZE = "Quantidade de registros por página (máximo 50).";

    // ===================== Respostas de sucesso =====================

    public static final String PEIXE_V1 = """
            {
              "id": 1,
              "nome": "Nemo",
              "idade": 2,
              "tamanho": 8.5,
              "especieId": 1,
              "aquarioId": 1,
              "alimentacaoIds": [1, 2],
              "_links": {
                "self":    { "href": "http://localhost:8080/peixes/1" },
                "update":  { "href": "http://localhost:8080/peixes/1" },
                "delete":  { "href": "http://localhost:8080/peixes/1" },
                "especie": { "href": "http://localhost:8080/especies/1" },
                "aquario": { "href": "http://localhost:8080/aquarios/1" },
                "peixes":  { "href": "http://localhost:8080/peixes" }
              }
            }""";

    public static final String PEIXE_V2 = """
            {
              "id": 1,
              "nome": "Nemo",
              "idade": 2,
              "faixaEtaria": "JOVEM",
              "tamanhoCm": 8.5,
              "tamanhoFormatado": "8,5 cm",
              "especie": { "id": 1, "nome": "Peixe-palhaço", "familia": "Pomacentridae" },
              "aquario": { "id": 1, "nome": "Aquário Principal" },
              "alimentacoes": [
                { "id": 1, "nome": "Ração Tropical", "tipo": "RACAO" },
                { "id": 2, "nome": "Artêmia Viva", "tipo": "VIVA" }
              ],
              "_links": {
                "self":    { "href": "http://localhost:8080/peixes/1" },
                "update":  { "href": "http://localhost:8080/peixes/1" },
                "delete":  { "href": "http://localhost:8080/peixes/1" },
                "especie": { "href": "http://localhost:8080/especies/1" },
                "aquario": { "href": "http://localhost:8080/aquarios/1" },
                "peixes":  { "href": "http://localhost:8080/peixes" }
              }
            }""";

    public static final String PEIXES_PAGINA_V1 = """
            {
              "_embedded": {
                "peixes": [
                  { "id": 1, "nome": "Nemo", "idade": 2, "tamanho": 8.5, "especieId": 1, "aquarioId": 1, "alimentacaoIds": [1, 2],
                    "_links": { "self": { "href": "http://localhost:8080/peixes/1" } } },
                  { "id": 2, "nome": "Marlin", "idade": 5, "tamanho": 10.0, "especieId": 1, "aquarioId": 1, "alimentacaoIds": [1],
                    "_links": { "self": { "href": "http://localhost:8080/peixes/2" } } }
                ]
              },
              "_links": {
                "self":  { "href": "http://localhost:8080/peixes?page=0&size=2&sort=nome,asc" },
                "first": { "href": "http://localhost:8080/peixes?page=0&size=2&sort=nome,asc" },
                "next":  { "href": "http://localhost:8080/peixes?page=1&size=2&sort=nome,asc" },
                "last":  { "href": "http://localhost:8080/peixes?page=2&size=2&sort=nome,asc" }
              },
              "page": { "size": 2, "totalElements": 5, "totalPages": 3, "number": 0 }
            }""";

    public static final String PEIXES_PAGINA_V2 = """
            {
              "_embedded": {
                "peixes": [
                  { "id": 3, "nome": "Dory", "idade": 3, "faixaEtaria": "JOVEM", "tamanhoCm": 25.0, "tamanhoFormatado": "25,0 cm",
                    "especie": { "id": 2, "nome": "Cirurgião-patela", "familia": "Acanthuridae" },
                    "aquario": { "id": 2, "nome": "Aquário Recifal" },
                    "alimentacoes": [ { "id": 1, "nome": "Ração Tropical", "tipo": "RACAO" },
                                      { "id": 3, "nome": "Spirulina em Flocos", "tipo": "VEGETAL" } ],
                    "_links": { "self": { "href": "http://localhost:8080/peixes/3" } } }
                ]
              },
              "_links": { "self": { "href": "http://localhost:8080/peixes?page=0&size=1&sort=tamanho,desc" } },
              "page": { "size": 1, "totalElements": 5, "totalPages": 5, "number": 0 }
            }""";

    // ===================== Erros =====================

    public static final String ERRO_400_VALIDACAO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 400,
              "erro": "Bad Request",
              "mensagem": "Um ou mais campos são inválidos. Corrija os campos listados em 'campos' e envie novamente.",
              "caminho": "/peixes",
              "campos": [
                { "campo": "nome", "mensagem": "O nome do peixe é obrigatório" },
                { "campo": "tamanho", "mensagem": "O tamanho deve ser maior que zero" }
              ]
            }""";

    public static final String ERRO_400_JSON = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 400,
              "erro": "Bad Request",
              "mensagem": "Corpo da requisição ausente ou mal formatado. Verifique se o JSON é válido e se os tipos dos campos estão corretos (ex.: 'idade' deve ser número inteiro; 'tipo' aceita RACAO, VIVA, VEGETAL ou CONGELADA; 'papel' aceita VISITANTE, CUIDADOR, BIOLOGO, GERENTE ou ADMIN).",
              "caminho": "/alimentacoes",
              "campos": []
            }""";

    public static final String ERRO_400_IDEMPOTENCIA = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 400,
              "erro": "Bad Request",
              "mensagem": "O header obrigatório 'X-Idempotency-Key' não foi informado. Toda requisição POST precisa de uma chave única (ex.: um UUID) para garantir idempotência.",
              "caminho": "/peixes",
              "campos": []
            }""";

    public static final String ERRO_400_ORDENACAO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 400,
              "erro": "Bad Request",
              "mensagem": "Campo de ordenação inválido: 'cor'. Campos permitidos: id, idade, nome, tamanho. Exemplo: sort=nome,asc",
              "caminho": "/peixes",
              "campos": []
            }""";

    public static final String ERRO_400_VERSAO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 400,
              "erro": "Bad Request",
              "mensagem": "Versão da API não suportada: '3'. Use o header X-API-Version com o valor 1 ou 2 (sem o header, a versão 1 é usada).",
              "caminho": "/peixes/1",
              "campos": []
            }""";

    public static final String ERRO_400_TIPO_ID = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 400,
              "erro": "Bad Request",
              "mensagem": "Valor 'abc' inválido para o parâmetro 'id'.",
              "caminho": "/peixes/abc",
              "campos": []
            }""";

    public static final String ERRO_401 = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 401,
              "erro": "Unauthorized",
              "mensagem": "API Key não informada. Envie o header X-API-Key (no Swagger, clique em 'Authorize').",
              "caminho": "/peixes",
              "campos": []
            }""";

    public static final String ERRO_403 = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 403,
              "erro": "Forbidden",
              "mensagem": "Seu papel CUIDADOR não permite excluir peixes. Papéis permitidos: BIOLOGO, GERENTE ou ADMIN.",
              "caminho": "/peixes/2",
              "campos": []
            }""";

    public static final String ERRO_403_ADMIN = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 403,
              "erro": "Forbidden",
              "mensagem": "Seu papel GERENTE não permite cadastrar API Keys. Papéis permitidos: ADMIN.",
              "caminho": "/api-keys",
              "campos": []
            }""";

    public static final String ERRO_415 = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 415,
              "erro": "Unsupported Media Type",
              "mensagem": "Content-Type 'text/plain' não suportado. Envie o corpo como application/json.",
              "caminho": "/peixes",
              "campos": []
            }""";

    public static final String ERRO_404_PEIXE = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 404,
              "erro": "Not Found",
              "mensagem": "Peixe com id 99 não encontrado.",
              "caminho": "/peixes/99",
              "campos": []
            }""";

    public static final String ERRO_404_RELACIONAMENTO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 404,
              "erro": "Not Found",
              "mensagem": "Espécie com id 99 não encontrada. Consulte GET /especies.",
              "caminho": "/peixes",
              "campos": []
            }""";

    public static final String ERRO_404_ESPECIE = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 404,
              "erro": "Not Found",
              "mensagem": "Espécie com id 99 não encontrada.",
              "caminho": "/especies/99",
              "campos": []
            }""";

    public static final String ERRO_404_AQUARIO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 404,
              "erro": "Not Found",
              "mensagem": "Aquário com id 99 não encontrado.",
              "caminho": "/aquarios/99",
              "campos": []
            }""";

    public static final String ERRO_404_CUIDADOR = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 404,
              "erro": "Not Found",
              "mensagem": "Nenhum cuidador encontrado com o e-mail 'pedro@email.com'.",
              "caminho": "/cuidadores/buscar",
              "campos": []
            }""";

    public static final String ERRO_404_ALIMENTACAO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 404,
              "erro": "Not Found",
              "mensagem": "Alimentação com id 99 não encontrada.",
              "caminho": "/alimentacoes/99",
              "campos": []
            }""";

    public static final String ERRO_404_API_KEY = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 404,
              "erro": "Not Found",
              "mensagem": "API Key com id 99 não encontrada.",
              "caminho": "/api-keys/99",
              "campos": []
            }""";

    public static final String ERRO_409_ESPECIE_DUPLICADA = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "Já existe uma espécie chamada 'Peixe-palhaço'.",
              "caminho": "/especies",
              "campos": []
            }""";

    public static final String ERRO_409_ESPECIE_EM_USO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "Não é possível excluir a espécie 'Peixe-palhaço': existem 2 peixe(s) cadastrados com ela. Altere ou exclua esses peixes primeiro.",
              "caminho": "/especies/1",
              "campos": []
            }""";

    public static final String ERRO_409_AQUARIO_DUPLICADO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "O cuidador 'João Silva' já é responsável pelo aquário 'Aquário Principal' (id 1). Cada cuidador cuida de apenas um aquário.",
              "caminho": "/aquarios",
              "campos": []
            }""";

    public static final String ERRO_409_AQUARIO_EM_USO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "Não é possível excluir o aquário 'Aquário Principal': ele possui 2 peixe(s). Transfira ou exclua os peixes primeiro.",
              "caminho": "/aquarios/1",
              "campos": []
            }""";

    public static final String ERRO_409_CUIDADOR_DUPLICADO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "Já existe um cuidador com o e-mail 'joao@email.com'.",
              "caminho": "/cuidadores",
              "campos": []
            }""";

    public static final String ERRO_409_CUIDADOR_EM_USO = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "Não é possível excluir o cuidador 'João Silva': ele é responsável pelo aquário 'Aquário Principal' (id 1). Remova-o do aquário (PUT /aquarios/1 sem cuidadorId) primeiro.",
              "caminho": "/cuidadores/1",
              "campos": []
            }""";

    public static final String ERRO_409_ALIMENTACAO_DUPLICADA = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "Já existe uma alimentação chamada 'Ração Tropical'.",
              "caminho": "/alimentacoes",
              "campos": []
            }""";

    public static final String ERRO_409_API_KEY_PROPRIA = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "Você não pode revogar a própria API Key que está usando nesta requisição.",
              "caminho": "/api-keys/1",
              "campos": []
            }""";

    public static final String ERRO_409_IDEMPOTENCIA = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 409,
              "erro": "Conflict",
              "mensagem": "A requisição com a chave de idempotência '3f2b8c1e-7d4a-4f6b-9c2e-1a5d8e7f9b30' ainda está em processamento. Aguarde e tente novamente.",
              "caminho": "/peixes",
              "campos": []
            }""";

    public static final String ERRO_422_IDEMPOTENCIA = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 422,
              "erro": "Unprocessable Entity",
              "mensagem": "A chave de idempotência '3f2b8c1e-7d4a-4f6b-9c2e-1a5d8e7f9b30' já foi usada em POST /peixes com outro corpo de requisição. Use uma nova chave (ex.: um novo UUID) para uma nova operação.",
              "caminho": "/peixes",
              "campos": []
            }""";

    public static final String ERRO_429 = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 429,
              "erro": "Too Many Requests",
              "mensagem": "Limite de requisições excedido: o balde de 60 requisições está vazio e é reabastecido com 1 requisição(ões) por segundo. Tente novamente em 1 segundo(s).",
              "caminho": "/peixes",
              "campos": []
            }""";

    public static final String ERRO_500 = """
            {
              "timestamp": "2026-11-12T22:30:00Z",
              "status": 500,
              "erro": "Internal Server Error",
              "mensagem": "Erro interno inesperado. Tente novamente; se o problema persistir, contate o suporte.",
              "caminho": "/peixes",
              "campos": []
            }""";
}
