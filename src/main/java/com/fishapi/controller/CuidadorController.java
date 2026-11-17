package com.fishapi.controller;

import com.fishapi.dto.CuidadorRequest;
import com.fishapi.dto.CuidadorResponse;
import com.fishapi.exception.ErroResponse;
import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.idempotencia.ResultadoIdempotente;
import com.fishapi.service.CuidadorService;

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
@RequestMapping("/cuidadores")
@Tag(name = "Cuidadores", description = """
        Cadastro dos **cuidadores**, as pessoas responsáveis pelos aquários. Cada cuidador cuida de no máximo um \
        aquário (relacionamento **One-to-One**). O e-mail é único.

        **Exemplos do que dá para fazer aqui:**
        - `GET /cuidadores`: lista João Silva, Ana Souza e Carlos Lima
        - `GET /cuidadores/1`: mostra João Silva e o aquário pelo qual ele é responsável (Aquário Principal)
        - `GET /cuidadores/buscar?email=joao@email.com`: encontra o cuidador pelo e-mail
        - `POST /cuidadores`: cadastra Marina Costa
        - `DELETE /cuidadores/1` → 409, porque João ainda é responsável pelo Aquário Principal
        """)
public class CuidadorController {

    private static final String SORT = "Ordenação no formato `campo,asc|desc`. Campos: `id`, `nome`, `email`.";

    private final CuidadorService service;
    private final IdempotenciaService idempotencia;

    public CuidadorController(CuidadorService service, IdempotenciaService idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    @Operation(summary = "Listar cuidadores (paginado)",
            description = "Retorna os cuidadores de forma paginada, com links HATEOAS de navegação. "
                    + "Exemplo: `GET /cuidadores?sort=nome,asc` → Ana Souza, Carlos Lima, João Silva.")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT,
                    schema = @Schema(type = "string", example = "nome,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de cuidadores"),
            @ApiResponse(responseCode = "400", description = "Campo de ordenação inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Ordenação inválida", value = ERRO_400_ORDENACAO)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<CuidadorResponse>>> listar(
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(Hateoas.paginar(service.listar(pageable), this::modelo));
    }

    @Operation(summary = "Buscar cuidador por ID",
            description = "Retorna um cuidador e o aquário sob sua responsabilidade (com link para ele). "
                    + "Exemplo: `GET /cuidadores/2` → Ana Souza, responsável pelo Aquário Recifal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuidador encontrado"),
            @ApiResponse(responseCode = "400", description = "ID em formato inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "ID inválido", value = ERRO_400_TIPO_ID))),
            @ApiResponse(responseCode = "404", description = "Cuidador não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Cuidador inexistente", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 404,
                                      "erro": "Not Found",
                                      "mensagem": "Cuidador com id 99 não encontrado.",
                                      "caminho": "/cuidadores/99",
                                      "campos": []
                                    }""")))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<CuidadorResponse>> buscarPorId(
            @Parameter(description = "ID do cuidador (1 = João Silva, 2 = Ana Souza, 3 = Carlos Lima)", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(modelo(service.buscarPorId(id)));
    }

    @Operation(summary = "Buscar cuidador por e-mail (consulta personalizada)",
            description = """
                    Consulta personalizada `findByEmailIgnoreCase`: busca exata pelo e-mail, sem diferenciar maiúsculas.

                    Exemplos: `email=joao@email.com` → João Silva · `email=ANA.SOUZA@EMAIL.COM` → Ana Souza.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuidador encontrado"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'email' ausente ou vazio",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Sem e-mail", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 400,
                                      "erro": "Bad Request",
                                      "mensagem": "O parâmetro obrigatório 'email' não foi informado.",
                                      "caminho": "/cuidadores/buscar",
                                      "campos": []
                                    }"""))),
            @ApiResponse(responseCode = "404", description = "Nenhum cuidador com esse e-mail",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "E-mail não cadastrado", value = ERRO_404_CUIDADOR)))
    })
    @GetMapping("/buscar")
    public ResponseEntity<EntityModel<CuidadorResponse>> buscarPorEmail(
            @Parameter(description = "E-mail do cuidador", example = "joao@email.com", required = true)
            @RequestParam String email) {
        return ResponseEntity.ok(modelo(service.buscarPorEmail(email)));
    }

    @Operation(summary = "Cadastrar cuidador (idempotente)",
            description = """
                    Cadastra um novo cuidador. O e-mail é salvo em minúsculas e não pode repetir (senão → 409).

                    **Idempotência:** header `X-Idempotency-Key` obrigatório.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuidador criado. O header Location aponta para ele.",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = CuidadorResponse.class),
                            examples = @ExampleObject(name = "Cuidador criado", value = """
                                    {
                                      "id": 4,
                                      "nome": "Marina Costa",
                                      "email": "marina.costa@email.com",
                                      "aquario": null,
                                      "_links": {
                                        "self":       { "href": "http://localhost:8080/cuidadores/4" },
                                        "update":     { "href": "http://localhost:8080/cuidadores/4" },
                                        "delete":     { "href": "http://localhost:8080/cuidadores/4" },
                                        "cuidadores": { "href": "http://localhost:8080/cuidadores" }
                                      }
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (ex.: e-mail mal formatado) ou header X-Idempotency-Key ausente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "E-mail inválido", value = """
                                            {
                                              "timestamp": "2026-11-12T22:30:00Z",
                                              "status": 400,
                                              "erro": "Bad Request",
                                              "mensagem": "Um ou mais campos são inválidos. Corrija os campos listados em 'campos' e envie novamente.",
                                              "caminho": "/cuidadores",
                                              "campos": [ { "campo": "email", "mensagem": "Informe um email válido" } ]
                                            }"""),
                                    @ExampleObject(name = "Sem X-Idempotency-Key", value = ERRO_400_IDEMPOTENCIA)
                            })),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado ou chave de idempotência em processamento",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "E-mail duplicado", value = ERRO_409_CUIDADOR_DUPLICADO))),
            @ApiResponse(responseCode = "422", description = "Chave de idempotência já usada com outro corpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave reutilizada", value = ERRO_422_IDEMPOTENCIA)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<CuidadorResponse>> criar(
            @Parameter(description = DESC_IDEMPOTENCY_KEY, example = "d8f3a6b2-cuidador-marina", required = true)
            @RequestHeader(IdempotenciaService.HEADER) String chaveIdempotencia,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Dados do novo cuidador", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CuidadorRequest.class),
                            examples = {
                                    @ExampleObject(name = "Marina Costa", value = """
                                            { "nome": "Marina Costa", "email": "marina.costa@email.com" }"""),
                                    @ExampleObject(name = "E-mail duplicado (gera 409)", value = """
                                            { "nome": "João Pedro Silva", "email": "joao@email.com" }"""),
                                    @ExampleObject(name = "E-mail inválido (gera 400)", value = """
                                            { "nome": "João", "email": "email-invalido" }""")
                            }))
            @Valid @RequestBody CuidadorRequest request) {

        ResultadoIdempotente<CuidadorResponse> resultado = idempotencia.executar(
                chaveIdempotencia, "POST /cuidadores", request,
                () -> service.criar(request), CuidadorResponse::id, service::buscarPorId);

        EntityModel<CuidadorResponse> modelo = modelo(resultado.resposta());
        return ResponseEntity
                .created(URI.create(modelo.getRequiredLink(IanaLinkRelations.SELF).getHref()))
                .header(IdempotenciaService.HEADER, chaveIdempotencia)
                .header(IdempotenciaService.HEADER_REPETIDA, String.valueOf(resultado.repetida()))
                .body(modelo);
    }

    @Operation(summary = "Atualizar cuidador",
            description = "Atualiza nome e e-mail de um cuidador. Exemplo: `PUT /cuidadores/3` atualiza o e-mail de Carlos Lima.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuidador atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "JSON inválido", value = ERRO_400_JSON))),
            @ApiResponse(responseCode = "404", description = "Cuidador não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Cuidador inexistente", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 404,
                                      "erro": "Not Found",
                                      "mensagem": "Cuidador com id 99 não encontrado.",
                                      "caminho": "/cuidadores/99",
                                      "campos": []
                                    }"""))),
            @ApiResponse(responseCode = "409", description = "Outro cuidador já usa esse e-mail",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "E-mail duplicado", value = ERRO_409_CUIDADOR_DUPLICADO)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<CuidadorResponse>> atualizar(
            @Parameter(description = "ID do cuidador a atualizar", example = "3") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Novos dados do cuidador", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CuidadorRequest.class),
                            examples = @ExampleObject(name = "Carlos Lima com novo e-mail", value = """
                                    { "nome": "Carlos Lima", "email": "carlos.lima@hookfishing.com" }""")))
            @Valid @RequestBody CuidadorRequest request) {
        return ResponseEntity.ok(modelo(service.atualizar(id, request)));
    }

    @Operation(summary = "Excluir cuidador",
            description = """
                    Exclui um cuidador que **não esteja responsável por nenhum aquário** (senão → 409).

                    Exemplo: `DELETE /cuidadores/3` exclui Carlos Lima (livre); `DELETE /cuidadores/1` → 409. \
                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cuidador excluído"),
            @ApiResponse(responseCode = "404", description = "Cuidador não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Cuidador inexistente", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 404,
                                      "erro": "Not Found",
                                      "mensagem": "Cuidador com id 99 não encontrado.",
                                      "caminho": "/cuidadores/99",
                                      "campos": []
                                    }"""))),
            @ApiResponse(responseCode = "409", description = "O cuidador ainda é responsável por um aquário",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Cuidador vinculado", value = ERRO_409_CUIDADOR_EM_USO)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID do cuidador a excluir", example = "3") @PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<CuidadorResponse> modelo(CuidadorResponse cuidador) {
        EntityModel<CuidadorResponse> modelo = EntityModel.of(cuidador,
                linkTo(CuidadorController.class).slash(cuidador.id()).withSelfRel(),
                linkTo(CuidadorController.class).slash(cuidador.id()).withRel("update"),
                linkTo(CuidadorController.class).slash(cuidador.id()).withRel("delete"));
        if (cuidador.aquario() != null) {
            modelo.add(linkTo(AquarioController.class).slash(cuidador.aquario().id()).withRel("aquario"));
        }
        modelo.add(linkTo(CuidadorController.class).withRel("cuidadores"));
        return modelo;
    }
}
