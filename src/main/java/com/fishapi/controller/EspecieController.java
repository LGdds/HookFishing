package com.fishapi.controller;

import com.fishapi.dto.EspecieRequest;
import com.fishapi.dto.EspecieResponse;
import com.fishapi.exception.ErroResponse;
import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.idempotencia.ResultadoIdempotente;
import com.fishapi.service.EspecieService;

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
@RequestMapping("/especies")
@Tag(name = "Espécies", description = """
        Catálogo de **espécies** de peixes (nome popular + família biológica). \
        Uma espécie possui vários peixes (relacionamento **One-to-Many**). O nome da espécie é único.

        **Exemplos do que dá para fazer aqui:**
        - `GET /especies?sort=nome,asc`: lista Betta, Cirurgião-patela, Peixe-palhaço e Tetra-neon
        - `GET /especies/1`: mostra o Peixe-palhaço e quantos peixes são dessa espécie
        - `GET /especies/buscar?familia=Pomacentridae`: espécies da família dos peixes-palhaço
        - `POST /especies`: cadastra o Peixe-anjo (Pomacanthidae)
        - `DELETE /especies/4`: só funciona se nenhum peixe usar a espécie (senão → 409)
        """)
public class EspecieController {

    private static final String SORT = "Ordenação no formato `campo,asc|desc`. Campos: `id`, `nome`, `familia`.";

    private final EspecieService service;
    private final IdempotenciaService idempotencia;

    public EspecieController(EspecieService service, IdempotenciaService idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    @Operation(summary = "Listar espécies (paginado)",
            description = "Retorna as espécies de forma paginada, com links HATEOAS de navegação. "
                    + "Exemplo: `GET /especies?page=0&size=10&sort=nome,asc`.")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT,
                    schema = @Schema(type = "string", example = "nome,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de espécies"),
            @ApiResponse(responseCode = "400", description = "Campo de ordenação inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Ordenação inválida", value = ERRO_400_ORDENACAO)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<EspecieResponse>>> listar(
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(Hateoas.paginar(service.listar(pageable), this::modelo));
    }

    @Operation(summary = "Buscar espécie por ID",
            description = "Retorna uma espécie e a quantidade de peixes cadastrados com ela. "
                    + "Exemplo: `GET /especies/1` → Peixe-palhaço (Pomacentridae), 2 peixes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espécie encontrada"),
            @ApiResponse(responseCode = "400", description = "ID em formato inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "ID inválido", value = ERRO_400_TIPO_ID))),
            @ApiResponse(responseCode = "404", description = "Espécie não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Espécie inexistente", value = ERRO_404_ESPECIE)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<EspecieResponse>> buscarPorId(
            @Parameter(description = "ID da espécie (1 = Peixe-palhaço, 2 = Cirurgião-patela, 3 = Betta, 4 = Tetra-neon)",
                    example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(modelo(service.buscarPorId(id)));
    }

    @Operation(summary = "Buscar espécies por família (consulta personalizada)",
            description = """
                    Consulta personalizada `findByFamiliaContainingIgnoreCase`: espécies cuja família contém o texto informado.

                    Exemplos: `familia=Pomacentridae` → Peixe-palhaço · `familia=idae` → todas.""")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT,
                    schema = @Schema(type = "string", example = "nome,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espécies encontradas (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'familia' ausente/vazio ou ordenação inválida",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Ordenação inválida", value = ERRO_400_ORDENACAO)))
    })
    @GetMapping("/buscar")
    public ResponseEntity<PagedModel<EntityModel<EspecieResponse>>> buscarPorFamilia(
            @Parameter(description = "Parte do nome da família", example = "Pomacentridae", required = true)
            @RequestParam String familia,
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(Hateoas.paginar(service.buscarPorFamilia(familia, pageable), this::modelo));
    }

    @Operation(summary = "Cadastrar espécie (idempotente)",
            description = """
                    Cadastra uma nova espécie. O nome não pode repetir um já existente (senão → 409).

                    **Idempotência:** header `X-Idempotency-Key` obrigatório; repetir a mesma chave com o mesmo corpo \
                    devolve a espécie já criada, sem duplicar.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Espécie criada. O header Location aponta para ela.",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = EspecieResponse.class),
                            examples = @ExampleObject(name = "Espécie criada", value = """
                                    {
                                      "id": 5,
                                      "nome": "Peixe-anjo",
                                      "familia": "Pomacanthidae",
                                      "quantidadePeixes": 0,
                                      "_links": {
                                        "self":     { "href": "http://localhost:8080/especies/5" },
                                        "update":   { "href": "http://localhost:8080/especies/5" },
                                        "delete":   { "href": "http://localhost:8080/especies/5" },
                                        "especies": { "href": "http://localhost:8080/especies" }
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
                                              "caminho": "/especies",
                                              "campos": [ { "campo": "familia", "mensagem": "A família é obrigatória" } ]
                                            }"""),
                                    @ExampleObject(name = "Sem X-Idempotency-Key", value = ERRO_400_IDEMPOTENCIA)
                            })),
            @ApiResponse(responseCode = "409", description = "Já existe espécie com esse nome, ou chave de idempotência em processamento",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Nome duplicado", value = ERRO_409_ESPECIE_DUPLICADA))),
            @ApiResponse(responseCode = "422", description = "Chave de idempotência já usada com outro corpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave reutilizada", value = ERRO_422_IDEMPOTENCIA)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<EspecieResponse>> criar(
            @Parameter(description = DESC_IDEMPOTENCY_KEY, example = "b7e1c0d2-especie-peixe-anjo", required = true)
            @RequestHeader(IdempotenciaService.HEADER) String chaveIdempotencia,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Dados da nova espécie", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EspecieRequest.class),
                            examples = {
                                    @ExampleObject(name = "Peixe-anjo", value = """
                                            { "nome": "Peixe-anjo", "familia": "Pomacanthidae" }"""),
                                    @ExampleObject(name = "Duplicada (gera 409)", value = """
                                            { "nome": "Peixe-palhaço", "familia": "Pomacentridae" }""")
                            }))
            @Valid @RequestBody EspecieRequest request) {

        ResultadoIdempotente<EspecieResponse> resultado = idempotencia.executar(
                chaveIdempotencia, "POST /especies", request,
                () -> service.criar(request), EspecieResponse::id, service::buscarPorId);

        EntityModel<EspecieResponse> modelo = modelo(resultado.resposta());
        return ResponseEntity
                .created(URI.create(modelo.getRequiredLink(IanaLinkRelations.SELF).getHref()))
                .header(IdempotenciaService.HEADER, chaveIdempotencia)
                .header(IdempotenciaService.HEADER_REPETIDA, String.valueOf(resultado.repetida()))
                .body(modelo);
    }

    @Operation(summary = "Atualizar espécie",
            description = "Atualiza nome e família de uma espécie. Exemplo: `PUT /especies/3` corrige o nome do Betta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espécie atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "JSON inválido", value = ERRO_400_JSON))),
            @ApiResponse(responseCode = "404", description = "Espécie não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Espécie inexistente", value = ERRO_404_ESPECIE))),
            @ApiResponse(responseCode = "409", description = "Outra espécie já usa esse nome",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Nome duplicado", value = ERRO_409_ESPECIE_DUPLICADA)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<EspecieResponse>> atualizar(
            @Parameter(description = "ID da espécie a atualizar", example = "3") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Novos dados da espécie", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EspecieRequest.class),
                            examples = @ExampleObject(name = "Betta-esplêndido", value = """
                                    { "nome": "Betta-esplêndido", "familia": "Osphronemidae" }""")))
            @Valid @RequestBody EspecieRequest request) {
        return ResponseEntity.ok(modelo(service.atualizar(id, request)));
    }

    @Operation(summary = "Excluir espécie",
            description = """
                    Exclui uma espécie **sem peixes vinculados**. Se algum peixe usar a espécie, retorna 409.

                    Exemplo: `DELETE /especies/1` → 409 (Nemo e Marlin são Peixe-palhaço). \
                    Cadastre o Peixe-anjo e exclua-o para ver o 204.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Espécie excluída"),
            @ApiResponse(responseCode = "404", description = "Espécie não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Espécie inexistente", value = ERRO_404_ESPECIE))),
            @ApiResponse(responseCode = "409", description = "A espécie ainda possui peixes",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Espécie em uso", value = ERRO_409_ESPECIE_EM_USO)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID da espécie a excluir", example = "5") @PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<EspecieResponse> modelo(EspecieResponse especie) {
        return EntityModel.of(especie,
                linkTo(EspecieController.class).slash(especie.id()).withSelfRel(),
                linkTo(EspecieController.class).slash(especie.id()).withRel("update"),
                linkTo(EspecieController.class).slash(especie.id()).withRel("delete"),
                linkTo(EspecieController.class).withRel("especies"));
    }
}
