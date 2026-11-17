package com.fishapi.controller;

import com.fishapi.dto.ApiKeyRequest;
import com.fishapi.dto.ApiKeyResponse;
import com.fishapi.dto.ApiKeyUpdateRequest;
import com.fishapi.exception.ErroResponse;
import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.idempotencia.ResultadoIdempotente;
import com.fishapi.seguranca.ApiKeyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static com.fishapi.config.ExemplosOpenApi.*;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@RestController
@RequestMapping("/api-keys")
@Tag(name = "API Keys", description = """
        Geração e gerenciamento das **chaves de API** usadas no header `X-API-Key`. \
        Exceto `GET /api-keys/me`, todos os endpoints desta aba exigem uma chave de nível **ADMIN** \
        (use `hf_admin_demo_2025` em *Authorize*).

        **Papéis:** `VISITANTE` (só consulta) · `CUIDADOR` (cadastra e edita peixes e alimentações) · `BIOLOGO` \
        (gerencia peixes, alimentações e espécies) · `GERENTE` (gerencia também aquários e cuidadores) · `ADMIN` (tudo + esta aba).

        **Exemplos do que dá para fazer aqui:**
        - `GET /api-keys/me`: descobre o dono, o papel e as permissões da chave que você está usando
        - `POST /api-keys`: gera uma chave CUIDADOR para o tablet da equipe de alimentação (o valor completo aparece **só nesta resposta**)
        - `PUT /api-keys/4` com `"papel": "BIOLOGO"`: promove a Equipe de Cuidadores a BIOLOGO
        - `PUT /api-keys/5` com `"ativa": false`: desativa a chave de visitante (ela passa a receber 401)
        - `POST /api-keys/4/regenerar`: troca o valor da chave da Equipe de Cuidadores
        - `DELETE /api-keys/5`: revoga definitivamente a chave de visitante
        """)
public class ApiKeyController {

    private static final String SORT = "Ordenação no formato `campo,asc|desc`. Campos: `id`, `nome`, `email`, `papel`, `criadaEm`.";

    private static final String EXEMPLO_CHAVE_CRIADA = """
            {
              "id": 6,
              "nome": "Tablet da Equipe de Alimentação",
              "email": "alimentacao@hookfishing.com",
              "papel": "CUIDADOR",
              "permissoes": {
                "peixes": ["CONSULTAR", "CADASTRAR", "EDITAR"],
                "alimentacoes": ["CONSULTAR", "CADASTRAR", "EDITAR"],
                "especies": ["CONSULTAR"],
                "aquarios": ["CONSULTAR"],
                "cuidadores": ["CONSULTAR"],
                "api-keys": []
              },
              "prefixo": "hf_Q2xhdWRl",
              "ativa": true,
              "criadaEm": "2026-11-12T22:30:00Z",
              "ultimoUsoEm": null,
              "chave": "hf_Q2xhdWRlIGVzdGV2ZSBhcXVpIDopKjFx",
              "aviso": "Guarde esta chave em local seguro: ela não será exibida novamente.",
              "_links": {
                "self":      { "href": "http://localhost:8080/api-keys/6" },
                "update":    { "href": "http://localhost:8080/api-keys/6" },
                "delete":    { "href": "http://localhost:8080/api-keys/6" },
                "regenerar": { "href": "http://localhost:8080/api-keys/6/regenerar" },
                "apiKeys":   { "href": "http://localhost:8080/api-keys" }
              }
            }""";

    private static final String EXEMPLO_CHAVE = """
            {
              "id": 4,
              "nome": "Equipe de Cuidadores",
              "email": "cuidador@hookfishing.com",
              "papel": "CUIDADOR",
              "permissoes": {
                "peixes": ["CONSULTAR", "CADASTRAR", "EDITAR"],
                "alimentacoes": ["CONSULTAR", "CADASTRAR", "EDITAR"],
                "especies": ["CONSULTAR"],
                "aquarios": ["CONSULTAR"],
                "cuidadores": ["CONSULTAR"],
                "api-keys": []
              },
              "prefixo": "hf_cuidador",
              "ativa": true,
              "criadaEm": "2026-11-12T22:00:00Z",
              "ultimoUsoEm": "2026-11-12T22:15:42Z",
              "chave": null,
              "aviso": null,
              "_links": {
                "self":      { "href": "http://localhost:8080/api-keys/4" },
                "update":    { "href": "http://localhost:8080/api-keys/4" },
                "delete":    { "href": "http://localhost:8080/api-keys/4" },
                "regenerar": { "href": "http://localhost:8080/api-keys/4/regenerar" },
                "apiKeys":   { "href": "http://localhost:8080/api-keys" }
              }
            }""";

    private final ApiKeyService service;
    private final IdempotenciaService idempotencia;

    public ApiKeyController(ApiKeyService service, IdempotenciaService idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    @Operation(summary = "Listar API Keys (ADMIN)",
            description = "Lista as chaves cadastradas (sem o valor completo, apenas o prefixo). "
                    + "Exemplo: `GET /api-keys?sort=papel,asc`.")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT,
                    schema = @Schema(type = "string", example = "id,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de API Keys"),
            @ApiResponse(responseCode = "400", description = "Campo de ordenação inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Ordenação inválida", value = ERRO_400_ORDENACAO)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<ApiKeyResponse>>> listar(
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(Hateoas.paginar(service.listar(pageable), this::modelo));
    }

    @Operation(summary = "Consultar a minha API Key",
            description = "Retorna os dados da chave enviada no header `X-API-Key`, incluindo o papel e as permissões em "
                    + "cada recurso. Disponível para **qualquer papel**; o front-end usa este endpoint para saber o que mostrar ao usuário.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados da chave em uso",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = ApiKeyResponse.class),
                            examples = @ExampleObject(name = "Chave CUIDADOR", value = EXEMPLO_CHAVE)))
    })
    @GetMapping("/me")
    public ResponseEntity<EntityModel<ApiKeyResponse>> minhaChave() {
        return ResponseEntity.ok(modelo(service.minhaChave()));
    }

    @Operation(summary = "Buscar API Key por ID (ADMIN)",
            description = "Exemplo: `GET /api-keys/4` → chave CUIDADOR da Equipe de Cuidadores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API Key encontrada",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = ApiKeyResponse.class),
                            examples = @ExampleObject(name = "Chave CUIDADOR", value = EXEMPLO_CHAVE))),
            @ApiResponse(responseCode = "404", description = "API Key não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave inexistente", value = ERRO_404_API_KEY)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<ApiKeyResponse>> buscarPorId(
            @Parameter(description = "ID da chave (1 = ADMIN, 2 = GERENTE, 3 = BIOLOGO, 4 = CUIDADOR, 5 = VISITANTE)", example = "4")
            @PathVariable Long id) {
        return ResponseEntity.ok(modelo(service.buscarPorId(id)));
    }

    @Operation(summary = "Gerar nova API Key (ADMIN, idempotente)",
            description = """
                    Gera uma nova chave para um usuário ou aplicação. **O valor completo (`chave`) aparece apenas nesta \
                    resposta**; a API guarda somente o hash SHA-256.

                    **Idempotência:** header `X-Idempotency-Key` obrigatório. Repetir a mesma requisição não gera uma \
                    segunda chave; por segurança, a repetição devolve os dados da chave **sem** o valor completo.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chave gerada. Copie o campo 'chave' agora.",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = ApiKeyResponse.class),
                            examples = @ExampleObject(name = "Chave gerada", value = EXEMPLO_CHAVE_CRIADA))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, papel fora do enum ou header X-Idempotency-Key ausente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "Campos inválidos", value = """
                                            {
                                              "timestamp": "2026-11-12T22:30:00Z",
                                              "status": 400,
                                              "erro": "Bad Request",
                                              "mensagem": "Um ou mais campos são inválidos. Corrija os campos listados em 'campos' e envie novamente.",
                                              "caminho": "/api-keys",
                                              "campos": [ { "campo": "papel", "mensagem": "O papel é obrigatório (VISITANTE, CUIDADOR, BIOLOGO, GERENTE ou ADMIN)" } ]
                                            }"""),
                                    @ExampleObject(name = "Sem X-Idempotency-Key", value = ERRO_400_IDEMPOTENCIA)
                            })),
            @ApiResponse(responseCode = "409", description = "Requisição com a mesma chave de idempotência ainda em processamento",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave em processamento", value = ERRO_409_IDEMPOTENCIA))),
            @ApiResponse(responseCode = "422", description = "Chave de idempotência já usada com outro corpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave reutilizada", value = ERRO_422_IDEMPOTENCIA)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<ApiKeyResponse>> criar(
            @Parameter(description = DESC_IDEMPOTENCY_KEY, example = "a1c3e5f7-apikey-frontend", required = true)
            @RequestHeader(IdempotenciaService.HEADER) String chaveIdempotencia,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Dono e papel da nova chave", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiKeyRequest.class),
                            examples = {
                                    @ExampleObject(name = "Tablet da alimentação (CUIDADOR)", value = """
                                            { "nome": "Tablet da Equipe de Alimentação", "email": "alimentacao@hookfishing.com", "papel": "CUIDADOR" }"""),
                                    @ExampleObject(name = "Laboratório (BIOLOGO)", value = """
                                            { "nome": "Laboratório de Biologia", "email": "laboratorio@hookfishing.com", "papel": "BIOLOGO" }"""),
                                    @ExampleObject(name = "Totem da recepção (VISITANTE)", value = """
                                            { "nome": "Totem da Recepção", "email": "recepcao@hookfishing.com", "papel": "VISITANTE" }""")
                            }))
            @Valid @RequestBody ApiKeyRequest request) {

        ResultadoIdempotente<ApiKeyResponse> resultado = idempotencia.executar(
                chaveIdempotencia, "POST /api-keys", request,
                () -> service.criar(request),
                ApiKeyResponse::id,
                id -> service.buscarPorId(id).comAviso(
                        "Requisição repetida (mesma X-Idempotency-Key): nenhuma chave nova foi gerada. "
                                + "Por segurança, o valor completo não é exibido novamente."));

        EntityModel<ApiKeyResponse> modelo = modelo(resultado.resposta());
        return ResponseEntity
                .created(URI.create(modelo.getRequiredLink(IanaLinkRelations.SELF).getHref()))
                .header(IdempotenciaService.HEADER, chaveIdempotencia)
                .header(IdempotenciaService.HEADER_REPETIDA, String.valueOf(resultado.repetida()))
                .body(modelo);
    }

    @Operation(summary = "Atualizar API Key (ADMIN)",
            description = """
                    Altera dono, papel ou status de uma chave. Com `"ativa": false` a chave passa a receber **401**.

                    Exemplo: `PUT /api-keys/4` promove a Equipe de Cuidadores a BIOLOGO. \
                    Não é permitido desativar ou rebaixar a própria chave em uso (→ 409).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API Key atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "JSON inválido", value = ERRO_400_JSON))),
            @ApiResponse(responseCode = "404", description = "API Key não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave inexistente", value = ERRO_404_API_KEY))),
            @ApiResponse(responseCode = "409", description = "Tentativa de desativar/rebaixar a própria chave",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Própria chave", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 409,
                                      "erro": "Conflict",
                                      "mensagem": "Você não pode desativar nem rebaixar a própria API Key que está usando nesta requisição.",
                                      "caminho": "/api-keys/1",
                                      "campos": []
                                    }""")))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<ApiKeyResponse>> atualizar(
            @Parameter(description = "ID da chave a atualizar", example = "4") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Novos dados da chave", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiKeyUpdateRequest.class),
                            examples = {
                                    @ExampleObject(name = "Promover cuidadores a BIOLOGO", value = """
                                            { "nome": "Equipe de Cuidadores", "email": "cuidador@hookfishing.com", "papel": "BIOLOGO", "ativa": true }"""),
                                    @ExampleObject(name = "Desativar a chave de visitante", value = """
                                            { "nome": "Visitante do Aquário", "email": "visitante@hookfishing.com", "papel": "VISITANTE", "ativa": false }""")
                            }))
            @Valid @RequestBody ApiKeyUpdateRequest request) {
        return ResponseEntity.ok(modelo(service.atualizar(id, request)));
    }

    @Operation(summary = "Regenerar API Key (ADMIN)",
            description = "Gera um novo valor para a chave; o valor antigo deixa de funcionar imediatamente. "
                    + "O novo valor aparece **apenas nesta resposta**. Exemplo: `POST /api-keys/4/regenerar`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Novo valor gerado",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = ApiKeyResponse.class),
                            examples = @ExampleObject(name = "Chave regenerada", value = """
                                    {
                                      "id": 4,
                                      "nome": "Equipe de Cuidadores",
                                      "email": "cuidador@hookfishing.com",
                                      "papel": "CUIDADOR",
                                      "prefixo": "hf_bm92YSBj",
                                      "ativa": true,
                                      "criadaEm": "2026-11-12T22:00:00Z",
                                      "ultimoUsoEm": "2026-11-12T22:15:42Z",
                                      "chave": "hf_bm92YSBjaGF2ZSBkZSBjdWlkYWRvcmVz",
                                      "aviso": "A chave anterior deixou de funcionar. Guarde esta chave em local seguro: ela não será exibida novamente.",
                                      "_links": { "self": { "href": "http://localhost:8080/api-keys/4" } }
                                    }"""))),
            @ApiResponse(responseCode = "404", description = "API Key não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave inexistente", value = ERRO_404_API_KEY)))
    })
    @PostMapping("/{id}/regenerar")
    public ResponseEntity<EntityModel<ApiKeyResponse>> regenerar(
            @Parameter(description = "ID da chave a regenerar", example = "4") @PathVariable Long id) {
        return ResponseEntity.ok(modelo(service.regenerar(id)));
    }

    @Operation(summary = "Revogar API Key (ADMIN)",
            description = "Exclui definitivamente uma chave. Exemplo: `DELETE /api-keys/5` revoga a chave de visitante. "
                    + "Não é permitido revogar a própria chave em uso (→ 409).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "API Key revogada"),
            @ApiResponse(responseCode = "404", description = "API Key não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave inexistente", value = ERRO_404_API_KEY))),
            @ApiResponse(responseCode = "409", description = "Tentativa de revogar a própria chave",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Própria chave", value = ERRO_409_API_KEY_PROPRIA)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revogar(
            @Parameter(description = "ID da chave a revogar", example = "5") @PathVariable Long id) {
        service.revogar(id);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<ApiKeyResponse> modelo(ApiKeyResponse chave) {
        return EntityModel.of(chave,
                linkTo(ApiKeyController.class).slash(chave.id()).withSelfRel(),
                linkTo(ApiKeyController.class).slash(chave.id()).withRel("update"),
                linkTo(ApiKeyController.class).slash(chave.id()).withRel("delete"),
                linkTo(ApiKeyController.class).slash(chave.id()).slash("regenerar").withRel("regenerar"),
                linkTo(ApiKeyController.class).withRel("apiKeys"));
    }
}
