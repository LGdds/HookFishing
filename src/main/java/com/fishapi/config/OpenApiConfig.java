package com.fishapi.config;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;
import com.fishapi.model.PapelAcesso;
import com.fishapi.seguranca.Permissoes;
import com.fishapi.seguranca.Permissoes.Operacao;
import com.fishapi.seguranca.Permissoes.Recurso;
import com.fishapi.versionamento.ApiVersionInterceptor;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Configuração da documentação OpenAPI.
 * O Swagger fica em http://localhost:8080/swagger-ui/index.html (página personalizada entregue pelo SwaggerUiFilter),
 * com um seletor entre as versões 1 e 2 (/v3/api-docs/v1 e /v3/api-docs/v2).
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_API_KEY = "ApiKeyAuth";
    public static final String ESQUEMA_LOGIN = "Login";

    private static final String DESCRICAO = """
            API RESTful da **HookFishing**, um sistema de gestão de aquários. Projeto final do curso de \
            Desenvolvimento de APIs com Spring Boot. Representa o dia a dia de um aquário: **peixes**, **espécies**, \
            **aquários**, **cuidadores** e **alimentações**, além das contas de usuário e das chaves de acesso.

            **Regras gerais**

            - Requisições e respostas em JSON (`application/json` e `application/hal+json`), codificação **UTF-8**.
            - Mensagens de erro em português (pt-BR), sempre no mesmo formato (`ErroResponse`).
            - Datas no formato ISO 8601 (UTC). Tamanhos em centímetros e capacidades em litros.
            - Listagens paginadas com `page`, `size` (máx. 50) e `sort` (ex.: `sort=nome,asc`), com links HATEOAS.
            - Erros: 400 requisição inválida, 401 sem chave, 403 papel sem permissão, 404 não encontrado, \
            405 método não suportado, 409 conflito, 415 tipo de conteúdo inválido, 422 chave de idempotência \
            reutilizada, 429 limite excedido, 500 erro interno.

            **Autenticação**

            - Clique em **Authorize** e entre com **e-mail e senha** (ex.: `admin@hookfishing.com` / `admin123`), \
            ou informe diretamente uma API Key no header `X-API-Key`.
            - Todas as rotas exigem a chave, exceto `/auth/cadastro`, `/auth/login` e `GET /planos`.
            - Não tem conta? Crie em `POST /auth/cadastro` e entre em `POST /auth/login`, que devolve a sua API Key. \
            Contas novas começam como `VISITANTE`; um `ADMIN` altera o papel em `PUT /api-keys/{id}`.

            **Papéis de acesso**

            | Papel | Peixes / Alimentações | Espécies | Aquários / Cuidadores | API Keys |
            |---|---|---|---|---|
            | `VISITANTE` | consultar | consultar | consultar | – |
            | `CUIDADOR` | consultar, cadastrar, editar | consultar | consultar | – |
            | `BIOLOGO` | tudo | tudo | consultar | – |
            | `GERENTE` | tudo | tudo | tudo | – |
            | `ADMIN` | tudo | tudo | tudo | tudo |

            Cada endpoint informa os papéis permitidos no fim da sua descrição.

            **Idempotência (X-Idempotency-Key)**

            - Todo POST de cadastro exige o header `X-Idempotency-Key` (ex.: um UUID).
            - Repetir a mesma chave com o mesmo corpo devolve o registro já criado, sem duplicar \
            (`X-Idempotent-Replayed: true`). Mesma chave com outro corpo: **422**. As chaves valem por 24 horas.

            **Planos e monetização**

            A API segue o modelo **freemium**: todo cadastro começa no plano `GRATUITO` e pode migrar para um plano \
            pago com limites maiores em `PUT /planos/assinatura` (simulação acadêmica, sem cobrança real). \
            A tabela de planos fica em `GET /planos` (pública).

            | Plano | Preço | Requisições de uma vez | Recarga |
            |---|---|---|---|
            | Sem chave (por IP) | – | 10 | 20 por minuto (1 a cada 3 s) |
            | `GRATUITO` | Grátis | 20 | 30 por minuto (1 a cada 2 s) |
            | `BASICO` | R$ 29,90/mês | 60 | 120 por minuto (2 por segundo) |
            | `PROFISSIONAL` | R$ 99,90/mês | 200 | 600 por minuto (10 por segundo) |

            Chaves de demonstração: `hf_admin_demo_2025` é PROFISSIONAL; gerente e biólogo são BASICO; \
            cuidador e visitante são GRATUITO.

            **Rate limit (token bucket) e bloqueio**

            Cada cliente tem um *balde* de fichas do tamanho do seu plano: cada requisição gasta 1 ficha e o balde é \
            reabastecido aos poucos. Dá para fazer uma rajada até o tamanho do balde; depois, só no ritmo da recarga.

            - Balde vazio: **429 Too Many Requests**, com `Retry-After` (segundos até a próxima ficha) e uma mensagem \
            dizendo quanto esperar e quantas tentativas faltam para o bloqueio.
            - **Bloqueio por insistência:** 5 requisições seguidas recusadas bloqueiam o cliente por **60 segundos**, \
            mesmo que o balde recarregue nesse meio-tempo. Um novo bloqueio em menos de 10 minutos **dobra** o tempo \
            (120 s, 240 s, 480 s...), até no máximo **15 minutos**. Durante o bloqueio, o 429 traz \
            `X-RateLimit-Blocked-Until` (horário de liberação, UTC) e o `Retry-After` com o tempo restante.
            - Esperar o `Retry-After` antes de tentar de novo zera a contagem de tentativas.
            - Limite global: a API inteira aceita até 1000 requisições de uma vez e 3000 por minuto.
            - Toda resposta traz `X-RateLimit-Limit` (capacidade), `X-RateLimit-Remaining` (fichas restantes), \
            `X-RateLimit-Reset` (segundos até o balde encher) e `X-RateLimit-Policy` (plano e recarga).
            - `GET /planos/limites` mostra o seu plano, as fichas restantes e as regras de bloqueio.

            **Versionamento (X-API-Version)**

            - Sem header = versão 1. `X-API-Version: 2` muda o formato de `GET /peixes` e `GET /peixes/{id}` \
            (relacionamentos detalhados e campos calculados).
            - Troque a versão em **Select a definition**, no topo da página: em cada versão o header já vem preenchido.

            **CORS e deploy (Render)**

            - Só as origens configuradas podem chamar a API pelo navegador (padrão: `localhost:3000`, `localhost:5173`, \
            `localhost:5500`, `127.0.0.1:5500` e `localhost:8080`). Métodos: GET, POST, PUT, DELETE e OPTIONS.
            - Headers liberados: `X-API-Key`, `Authorization`, `X-API-Version`, `X-Idempotency-Key`. O navegador \
            também consegue ler `Retry-After`, `X-RateLimit-*` e `Location` das respostas.
            - No Render, a URL pública do serviço é liberada automaticamente (variável `RENDER_EXTERNAL_URL`); \
            outras origens entram na variável `HOOKFISHING_CORS_ORIGINS`. A porta vem da variável `PORT`.

            **Dados de demonstração**

            - Usuários: `admin@hookfishing.com` / `admin123`, `gerente@hookfishing.com` / `gerente123`, \
            `biologo@hookfishing.com` / `biologo123`, `cuidador@hookfishing.com` / `cuidador123`, \
            `visitante@hookfishing.com` / `visitante123`.
            - API Keys: `hf_admin_demo_2025`, `hf_gerente_demo_2025`, `hf_biologo_demo_2025`, \
            `hf_cuidador_demo_2025`, `hf_visitante_demo_2025`.
            - Já cadastrados: peixes Nemo, Marlin, Dory, Azulão e Faísca; aquários Principal, Recifal e Comunitário.

            **Termos de uso (resumo)**

            - Use a API para o fim a que se destina: gerenciar aquários (ou estudar e testar o projeto).
            - Envie dados verdadeiros e respeite a privacidade dos dados pessoais de usuários e cuidadores (LGPD).
            - Não compartilhe sua API Key nem tente burlar o rate limit ou as permissões: o acesso pode ser **desativado**.
            - Exclusões não podem ser desfeitas.
            - Banco em memória: os dados são apagados ao reiniciar.
            """;

    @Bean
    public OpenAPI hookFishingOpenAPI() {
        return new OpenAPI()
                // Endereço relativo: o "Try it out" chama sempre o mesmo endereço da página do Swagger.
                // Sem isso, atrás do proxy HTTPS do Render o springdoc podia anunciar http://..., e o navegador
                // bloqueava a chamada ("Failed to fetch ... CORS").
                .servers(List.of(new Server().url("/").description("Mesmo endereço desta página")))
                .info(new Info()
                        .title("HookFishing API")
                        .version("2.0.0")
                        .description(DESCRICAO)
                        .termsOfService("https://github.com/LGdds/HookFishing#termos-de-servi%C3%A7o")
                        .contact(new Contact()
                                .name("HookFishing")
                                .email("contato@hookfishing.com")
                                .url("https://github.com/LGdds/HookFishing"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://github.com/LGdds/HookFishing/blob/main/LICENSE")))
                .externalDocs(new ExternalDocumentation()
                        .description("Repositório do projeto")
                        .url("https://github.com/LGdds/HookFishing"))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_LOGIN, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("""
                                        Entre com o **e-mail e a senha** de um usuário cadastrado. A API devolve a chave \
                                        do usuário, que o Swagger passa a enviar em todas as requisições.
                                        Ex.: `admin@hookfishing.com` / `admin123` · `cuidador@hookfishing.com` / `cuidador123` · \
                                        `visitante@hookfishing.com` / `visitante123`.""")
                                .flows(new OAuthFlows().password(new OAuthFlow()
                                        .tokenUrl("/auth/token")
                                        .scopes(new Scopes()))))
                        .addSecuritySchemes(ESQUEMA_API_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("""
                                        Alternativa ao login: informe diretamente uma API Key. Chaves de teste: \
                                        `hf_admin_demo_2025`, `hf_gerente_demo_2025`, `hf_biologo_demo_2025`, \
                                        `hf_cuidador_demo_2025`, `hf_visitante_demo_2025`.""")))
                // Qualquer um dos dois esquemas autentica a requisição
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_LOGIN))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_API_KEY));
    }

    // ===================== Versões (seletor no topo do Swagger) =====================

    @Bean
    public GroupedOpenApi documentacaoV1() {
        return grupo("v1", "1");
    }

    @Bean
    public GroupedOpenApi documentacaoV2() {
        return grupo("v2", "2");
    }

    /**
     * Cada versão vira um grupo do springdoc (/v3/api-docs/v1 e /v3/api-docs/v2).
     * O Swagger personalizado (src/main/resources/swagger/index.html) mostra os grupos num seletor de versão.
     */
    private GroupedOpenApi grupo(String nome, String versao) {
        return GroupedOpenApi.builder()
                .group(nome)
                .displayName("Versão " + versao)
                .pathsToMatch("/**")
                .addOpenApiCustomizer(respostasGlobais())
                .addOpenApiCustomizer(openApi -> personalizarVersao(openApi, versao))
                .build();
    }

    private static void personalizarVersao(OpenAPI openApi, String versao) {
        Info base = openApi.getInfo() == null ? new Info() : openApi.getInfo();
        // Novo objeto Info: o objeto base é compartilhado entre os grupos
        openApi.setInfo(new Info()
                .title(base.getTitle())
                .version(versao + ".0.0")
                .description(base.getDescription())
                .termsOfService(base.getTermsOfService())
                .contact(base.getContact())
                .license(base.getLicense()));

        if (openApi.getPaths() == null) {
            return;
        }
        String outroExemplo = "1".equals(versao) ? "Versão 2 (X-API-Version: 2)" : "Versão 1 (padrão)";

        openApi.getPaths().forEach((caminho, item) -> item.readOperations().forEach(operacao -> {
            Parameter parametro = operacao.getParameters() == null ? null : operacao.getParameters().stream()
                    .filter(p -> ApiVersionInterceptor.HEADER.equalsIgnoreCase(p.getName()) && "header".equals(p.getIn()))
                    .findFirst()
                    .orElse(null);

            if (parametro == null) {
                parametro = new HeaderParameter()
                        .name(ApiVersionInterceptor.HEADER)
                        .required(false)
                        .description("Versão da API (`1` ou `2`). Sem o header, a versão 1 é usada.");
                operacao.addParametersItem(parametro);
            }
            StringSchema schema = new StringSchema();
            schema.setEnum(List.of("1", "2"));
            schema.setDefault(versao);
            parametro.setSchema(schema);
            parametro.setExample(versao);

            // Nos endpoints com resposta diferente por versão, mostra só o exemplo da versão escolhida
            if (operacao.getResponses() != null && operacao.getResponses().get("200") != null
                    && operacao.getResponses().get("200").getContent() != null) {
                operacao.getResponses().get("200").getContent().values().forEach(midia -> {
                    if (midia.getExamples() != null && midia.getExamples().size() > 1) {
                        midia.getExamples().remove(outroExemplo);
                    }
                });
            }
        }));
    }

    // ===================== Respostas comuns =====================

    /**
     * Acrescenta a TODAS as operações as respostas comuns da Parte 2 (401, 403, 415, 429, 500)
     * e os headers de rate limit nas respostas de sucesso, e ordena os códigos.
     */
    private static OpenApiCustomizer respostasGlobais() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((caminho, item) -> item.readOperationsMap().forEach((metodo, operacao) -> {
                ApiResponses respostas = operacao.getResponses() == null ? new ApiResponses() : operacao.getResponses();
                if (caminho.startsWith("/auth") || (caminho.equals("/planos") && metodo == PathItem.HttpMethod.GET)) {
                    // Rotas públicas: sem X-API-Key, mas com rate limiting
                    operacao.setSecurity(new ArrayList<>());
                    String descricaoPublica = operacao.getDescription() == null ? "" : operacao.getDescription();
                    if (!descricaoPublica.contains("**Acesso:**")) {
                        operacao.setDescription(descricaoPublica + "\n\n**Acesso:** público (não exige `X-API-Key`).");
                    }
                    adicionar(respostas, "429",
                            "Limite excedido: sem chave, 10 requisições de uma vez por IP e recarga de 20 por minuto. Insistir 5 vezes bloqueia por 60 s (veja Retry-After e X-RateLimit-Blocked-Until)",
                            ExemplosOpenApi.ERRO_429, true);
                    adicionar(respostas, "500", "Erro interno inesperado", ExemplosOpenApi.ERRO_500, false);
                    ordenar(operacao, respostas);
                    return;
                }

                // Papéis permitidos nesta operação, calculados pela mesma matriz usada no ApiKeyInterceptor
                Optional<Recurso> recurso = Recurso.doCaminho(caminho);
                boolean propriaChave = caminho.equals("/api-keys/me");
                List<PapelAcesso> permitidos = propriaChave || recurso.isEmpty()
                        ? List.of(PapelAcesso.values())
                        : Permissoes.papeisPermitidos(recurso.get(), Operacao.doMetodo(metodo.name()));

                String sufixo = "\n\n**Papéis permitidos:** " + permitidos.stream()
                        .map(p -> "`" + p.name() + "`").collect(Collectors.joining(" · "));
                String descricao = operacao.getDescription() == null ? "" : operacao.getDescription();
                if (!descricao.contains("**Papéis permitidos:**")) {
                    operacao.setDescription(descricao + sufixo);
                }

                adicionar(respostas, "401",
                        "Não autenticado: header X-API-Key ausente, inválido ou de uma chave desativada",
                        ExemplosOpenApi.ERRO_401, false);
                if (recurso.isPresent() && permitidos.size() < PapelAcesso.values().length) {
                    Operacao op = Operacao.doMetodo(metodo.name());
                    // Exemplo gerado com o papel "mais alto" que NÃO pode fazer esta operação
                    PapelAcesso negado = java.util.Arrays.stream(PapelAcesso.values())
                            .filter(papel -> !permitidos.contains(papel))
                            .reduce((primeiro, segundo) -> segundo)
                            .orElse(PapelAcesso.VISITANTE);
                    String mensagem = "Seu papel " + negado + " não permite " + op.getVerbo() + " "
                            + recurso.get().getRotulo() + ". Papéis permitidos: " + Permissoes.listar(permitidos) + ".";
                    String exemplo = "{\"timestamp\": \"2026-11-12T22:30:00Z\", \"status\": 403, \"erro\": \"Forbidden\", "
                            + "\"mensagem\": \"" + mensagem + "\", \"caminho\": \"" + caminho + "\", \"campos\": []}";
                    adicionar(respostas, "403",
                            "Acesso negado: o papel da API Key não permite esta operação. Papéis permitidos: "
                                    + Permissoes.listar(permitidos),
                            exemplo, false);
                }
                if (metodo == PathItem.HttpMethod.POST || metodo == PathItem.HttpMethod.PUT) {
                    adicionar(respostas, "415",
                            "Corpo enviado sem Content-Type: application/json",
                            ExemplosOpenApi.ERRO_415, false);
                }
                adicionar(respostas, "429",
                        "Limite do plano excedido (token bucket). Insistir 5 vezes seguidas bloqueia por 60 s, dobrando a cada reincidência até 15 min. Veja Retry-After, X-RateLimit-Blocked-Until e GET /planos/limites",
                        ExemplosOpenApi.ERRO_429, true);
                adicionar(respostas, "500", "Erro interno inesperado", ExemplosOpenApi.ERRO_500, false);

                respostas.forEach((codigo, resposta) -> {
                    if (codigo.startsWith("2")) {
                        resposta.addHeaderObject("X-RateLimit-Limit",
                                cabecalho("Capacidade do balde (token bucket) do plano", 200));
                        resposta.addHeaderObject("X-RateLimit-Remaining",
                                cabecalho("Fichas (requisições) restantes no balde", 199));
                        resposta.addHeaderObject("X-RateLimit-Policy",
                                new Header().description("Plano e regra aplicados à chave")
                                        .schema(new StringSchema().example("PROFISSIONAL;capacidade=200;recarga=600/min")));
                        resposta.addHeaderObject("X-RateLimit-Reset",
                                cabecalho("Segundos até o balde ficar cheio de novo", 1));
                        resposta.addHeaderObject("X-API-Version",
                                new Header().description("Versão da API usada para responder (1 ou 2)")
                                        .schema(new StringSchema().example("1")));
                    }
                });

                ordenar(operacao, respostas);
            }));
        };
    }

    private static void ordenar(io.swagger.v3.oas.models.Operation operacao, ApiResponses respostas) {
        ApiResponses ordenadas = new ApiResponses();
        new TreeMap<>(respostas).forEach(ordenadas::addApiResponse);
        operacao.setResponses(ordenadas);
    }

    private static void adicionar(ApiResponses respostas, String codigo, String descricao,
                                  String exemploJson, boolean comRetryAfter) {
        if (respostas.containsKey(codigo)) {
            return;
        }
        MediaType midia = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ErroResponse"))
                .addExamples("exemplo", new Example().value(json(exemploJson)));

        ApiResponse resposta = new ApiResponse()
                .description(descricao)
                .content(new Content().addMediaType("application/json", midia));

        if (comRetryAfter) {
            resposta.addHeaderObject("Retry-After",
                    cabecalho("Segundos que o cliente deve aguardar antes de tentar novamente", 2));
            resposta.addHeaderObject("X-RateLimit-Blocked-Until",
                    new Header().description("Só quando o cliente foi bloqueado por insistir: horário (UTC) em que o acesso volta")
                            .schema(new StringSchema().example("2026-11-12T22:31:00Z")));
            resposta.addHeaderObject("X-RateLimit-Policy",
                    new Header().description("Plano e regra aplicados")
                            .schema(new StringSchema().example("GRATUITO;capacidade=20;recarga=30/min")));
        }
        respostas.addApiResponse(codigo, resposta);
    }

    private static Header cabecalho(String descricao, int exemplo) {
        return new Header().description(descricao).schema(new IntegerSchema().example(exemplo));
    }

    private static Object json(String valor) {
        try {
            return Json.mapper().readTree(valor);
        } catch (Exception e) {
            return valor;
        }
    }
}
