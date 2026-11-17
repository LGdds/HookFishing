package com.fishapi.controller;

import com.fishapi.dto.AquarioRequest;
import com.fishapi.dto.AquarioResponse;
import com.fishapi.exception.ErroResponse;
import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.idempotencia.ResultadoIdempotente;
import com.fishapi.service.AquarioService;

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
@RequestMapping("/aquarios")
@Tag(name = "Aquários", description = """
        Cadastro dos **aquários**. Cada aquário tem no máximo um **cuidador** (relacionamento **One-to-One**) \
        e abriga vários **peixes** (relacionamento **One-to-Many**). O nome do aquário é único.

        **Exemplos do que dá para fazer aqui:**
        - `GET /aquarios`: lista Aquário Principal, Aquário Recifal e Aquário Comunitário
        - `GET /aquarios/1`: mostra o Aquário Principal, o cuidador João Silva e a quantidade de peixes
        - `GET /aquarios/buscar?capacidadeMinima=200`: aquários com 200 litros ou mais
        - `POST /aquarios`: cria o Aquário Quarentena sob os cuidados de Carlos Lima (cuidador 3, que está livre)
        - `PUT /aquarios/3` com `cuidadorId: 1` → 409, porque João Silva já cuida do Aquário Principal
        """)
public class AquarioController {

    private static final String SORT = "Ordenação no formato `campo,asc|desc`. Campos: `id`, `nome`, `capacidadeLitros`.";

    private final AquarioService service;
    private final IdempotenciaService idempotencia;

    public AquarioController(AquarioService service, IdempotenciaService idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    @Operation(summary = "Listar aquários (paginado)",
            description = "Retorna os aquários de forma paginada, com links HATEOAS de navegação. "
                    + "Exemplo: `GET /aquarios?sort=capacidadeLitros,desc` → Recifal (350 L), Principal (200 L), Comunitário (120 L).")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT,
                    schema = @Schema(type = "string", example = "capacidadeLitros,desc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de aquários"),
            @ApiResponse(responseCode = "400", description = "Campo de ordenação inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Ordenação inválida", value = ERRO_400_ORDENACAO)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<AquarioResponse>>> listar(
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(Hateoas.paginar(service.listar(pageable), this::modelo));
    }

    @Operation(summary = "Buscar aquário por ID",
            description = "Retorna um aquário com seu cuidador e a quantidade de peixes, além de links para o cuidador. "
                    + "Exemplo: `GET /aquarios/1` → Aquário Principal, 200 L, cuidador João Silva, 2 peixes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aquário encontrado"),
            @ApiResponse(responseCode = "400", description = "ID em formato inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "ID inválido", value = ERRO_400_TIPO_ID))),
            @ApiResponse(responseCode = "404", description = "Aquário não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Aquário inexistente", value = ERRO_404_AQUARIO)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<AquarioResponse>> buscarPorId(
            @Parameter(description = "ID do aquário (1 = Principal, 2 = Recifal, 3 = Comunitário)", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(modelo(service.buscarPorId(id)));
    }

    @Operation(summary = "Buscar aquários por capacidade mínima (consulta personalizada)",
            description = """
                    Consulta personalizada `findByCapacidadeLitrosGreaterThanEqual`: aquários com capacidade \
                    **maior ou igual** ao valor informado (em litros).

                    Exemplos: `capacidadeMinima=200` → Principal e Recifal · `capacidadeMinima=300` → Recifal.""")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT,
                    schema = @Schema(type = "string", example = "capacidadeLitros,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aquários encontrados (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'capacidadeMinima' ausente, não numérico ou menor que 1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Capacidade inválida", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 400,
                                      "erro": "Bad Request",
                                      "mensagem": "O parâmetro 'capacidadeMinima' deve ser um número inteiro maior ou igual a 1.",
                                      "caminho": "/aquarios/buscar",
                                      "campos": []
                                    }""")))
    })
    @GetMapping("/buscar")
    public ResponseEntity<PagedModel<EntityModel<AquarioResponse>>> buscarPorCapacidade(
            @Parameter(description = "Capacidade mínima em litros", example = "200", required = true)
            @RequestParam Integer capacidadeMinima,
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "capacidadeLitros") Pageable pageable) {
        return ResponseEntity.ok(Hateoas.paginar(service.buscarPorCapacidadeMinima(capacidadeMinima, pageable), this::modelo));
    }

    @Operation(summary = "Cadastrar aquário (idempotente)",
            description = """
                    Cadastra um novo aquário, opcionalmente com um cuidador. O cuidador não pode estar \
                    cuidando de outro aquário (regra do One-to-One → 409).

                    **Idempotência:** header `X-Idempotency-Key` obrigatório.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aquário criado. O header Location aponta para ele.",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = AquarioResponse.class),
                            examples = @ExampleObject(name = "Aquário criado", value = """
                                    {
                                      "id": 4,
                                      "nome": "Aquário Quarentena",
                                      "capacidadeLitros": 80,
                                      "cuidador": { "id": 3, "nome": "Carlos Lima" },
                                      "quantidadePeixes": 0,
                                      "_links": {
                                        "self":     { "href": "http://localhost:8080/aquarios/4" },
                                        "update":   { "href": "http://localhost:8080/aquarios/4" },
                                        "delete":   { "href": "http://localhost:8080/aquarios/4" },
                                        "cuidador": { "href": "http://localhost:8080/cuidadores/3" },
                                        "aquarios": { "href": "http://localhost:8080/aquarios" }
                                      }
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou header X-Idempotency-Key ausente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "Campos inválidos", value = """
                                            {
                                              "timestamp": "2026-11-12T22:30:00Z",
                                              "status": 400,
                                              "erro": "Bad Request",
                                              "mensagem": "Um ou mais campos são inválidos. Corrija os campos listados em 'campos' e envie novamente.",
                                              "caminho": "/aquarios",
                                              "campos": [ { "campo": "capacidadeLitros", "mensagem": "A capacidade deve ser de pelo menos 1 litro" } ]
                                            }"""),
                                    @ExampleObject(name = "Sem X-Idempotency-Key", value = ERRO_400_IDEMPOTENCIA)
                            })),
            @ApiResponse(responseCode = "404", description = "Cuidador informado não existe",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Cuidador inexistente", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 404,
                                      "erro": "Not Found",
                                      "mensagem": "Cuidador com id 99 não encontrado. Consulte GET /cuidadores.",
                                      "caminho": "/aquarios",
                                      "campos": []
                                    }"""))),
            @ApiResponse(responseCode = "409", description = "Nome duplicado, cuidador já vinculado a outro aquário ou chave de idempotência em processamento",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Cuidador ocupado", value = ERRO_409_AQUARIO_DUPLICADO))),
            @ApiResponse(responseCode = "422", description = "Chave de idempotência já usada com outro corpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave reutilizada", value = ERRO_422_IDEMPOTENCIA)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<AquarioResponse>> criar(
            @Parameter(description = DESC_IDEMPOTENCY_KEY, example = "c4a9e2f1-aquario-quarentena", required = true)
            @RequestHeader(IdempotenciaService.HEADER) String chaveIdempotencia,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Dados do novo aquário", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AquarioRequest.class),
                            examples = {
                                    @ExampleObject(name = "Quarentena com Carlos Lima", value = """
                                            { "nome": "Aquário Quarentena", "capacidadeLitros": 80, "cuidadorId": 3 }"""),
                                    @ExampleObject(name = "Sem cuidador", value = """
                                            { "nome": "Aquário de Água Doce", "capacidadeLitros": 150 }"""),
                                    @ExampleObject(name = "Cuidador ocupado (gera 409)", value = """
                                            { "nome": "Aquário Plantado", "capacidadeLitros": 100, "cuidadorId": 1 }""")
                            }))
            @Valid @RequestBody AquarioRequest request) {

        ResultadoIdempotente<AquarioResponse> resultado = idempotencia.executar(
                chaveIdempotencia, "POST /aquarios", request,
                () -> service.criar(request), AquarioResponse::id, service::buscarPorId);

        EntityModel<AquarioResponse> modelo = modelo(resultado.resposta());
        return ResponseEntity
                .created(URI.create(modelo.getRequiredLink(IanaLinkRelations.SELF).getHref()))
                .header(IdempotenciaService.HEADER, chaveIdempotencia)
                .header(IdempotenciaService.HEADER_REPETIDA, String.valueOf(resultado.repetida()))
                .body(modelo);
    }

    @Operation(summary = "Atualizar aquário",
            description = """
                    Atualiza os dados de um aquário. Para **remover o cuidador**, envie o corpo sem `cuidadorId`.

                    Exemplo: `PUT /aquarios/3` passa o Aquário Comunitário para 150 L sob os cuidados de Carlos Lima. \
                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aquário atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "JSON inválido", value = ERRO_400_JSON))),
            @ApiResponse(responseCode = "404", description = "Aquário ou cuidador não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Aquário inexistente", value = ERRO_404_AQUARIO))),
            @ApiResponse(responseCode = "409", description = "Nome duplicado ou cuidador já vinculado a outro aquário",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Cuidador ocupado", value = ERRO_409_AQUARIO_DUPLICADO)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<AquarioResponse>> atualizar(
            @Parameter(description = "ID do aquário a atualizar", example = "3") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Novos dados do aquário", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AquarioRequest.class),
                            examples = @ExampleObject(name = "Comunitário com Carlos Lima", value = """
                                    { "nome": "Aquário Comunitário", "capacidadeLitros": 150, "cuidadorId": 3 }""")))
            @Valid @RequestBody AquarioRequest request) {
        return ResponseEntity.ok(modelo(service.atualizar(id, request)));
    }

    @Operation(summary = "Excluir aquário",
            description = """
                    Exclui um aquário **vazio**. Se ainda houver peixes nele, retorna 409.

                    Exemplo: `DELETE /aquarios/1` → 409 (Nemo e Marlin moram lá).""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Aquário excluído"),
            @ApiResponse(responseCode = "404", description = "Aquário não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Aquário inexistente", value = ERRO_404_AQUARIO))),
            @ApiResponse(responseCode = "409", description = "O aquário ainda possui peixes",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Aquário com peixes", value = ERRO_409_AQUARIO_EM_USO)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID do aquário a excluir", example = "4") @PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<AquarioResponse> modelo(AquarioResponse aquario) {
        EntityModel<AquarioResponse> modelo = EntityModel.of(aquario,
                linkTo(AquarioController.class).slash(aquario.id()).withSelfRel(),
                linkTo(AquarioController.class).slash(aquario.id()).withRel("update"),
                linkTo(AquarioController.class).slash(aquario.id()).withRel("delete"));
        if (aquario.cuidador() != null) {
            modelo.add(linkTo(CuidadorController.class).slash(aquario.cuidador().id()).withRel("cuidador"));
        }
        modelo.add(linkTo(AquarioController.class).withRel("aquarios"));
        return modelo;
    }
}
