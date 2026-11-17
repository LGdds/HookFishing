package com.fishapi.controller;

import com.fishapi.dto.AlimentacaoRequest;
import com.fishapi.dto.AlimentacaoResponse;
import com.fishapi.exception.ErroResponse;
import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.idempotencia.ResultadoIdempotente;
import com.fishapi.model.TipoAlimentacao;
import com.fishapi.service.AlimentacaoService;

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
@RequestMapping("/alimentacoes")
@Tag(name = "Alimentações", description = """
        Cadastro das **alimentações** oferecidas aos peixes. Um peixe recebe várias alimentações e uma alimentação \
        serve vários peixes (relacionamento **Many-to-Many**, tabela `peixe_alimentacao`). \
        O campo `tipo` é o **enum** `TipoAlimentacao`: `RACAO`, `VIVA`, `VEGETAL` ou `CONGELADA`.

        **Exemplos do que dá para fazer aqui:**
        - `GET /alimentacoes`: lista Ração Tropical, Artêmia Viva, Spirulina em Flocos e Larva de Mosquito Congelada
        - `GET /alimentacoes/1`: mostra a Ração Tropical e quantos peixes a recebem
        - `GET /alimentacoes/buscar?tipo=VEGETAL`: filtra pelo enum
        - `POST /alimentacoes`: cadastra o Krill Congelado
        - `DELETE /alimentacoes/4`: remove a alimentação e a tira da dieta dos peixes (Azulão)
        """)
public class AlimentacaoController {

    private static final String SORT = "Ordenação no formato `campo,asc|desc`. Campos: `id`, `nome`, `tipo`.";

    private final AlimentacaoService service;
    private final IdempotenciaService idempotencia;

    public AlimentacaoController(AlimentacaoService service, IdempotenciaService idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    @Operation(summary = "Listar alimentações (paginado)",
            description = "Retorna as alimentações de forma paginada, com links HATEOAS de navegação. "
                    + "Exemplo: `GET /alimentacoes?sort=nome,asc`.")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT,
                    schema = @Schema(type = "string", example = "nome,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de alimentações"),
            @ApiResponse(responseCode = "400", description = "Campo de ordenação inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Ordenação inválida", value = ERRO_400_ORDENACAO)))
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<AlimentacaoResponse>>> listar(
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(Hateoas.paginar(service.listar(pageable), this::modelo));
    }

    @Operation(summary = "Buscar alimentação por ID",
            description = "Retorna uma alimentação e quantos peixes a recebem. "
                    + "Exemplo: `GET /alimentacoes/1` → Ração Tropical (RACAO), 4 peixes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alimentação encontrada"),
            @ApiResponse(responseCode = "400", description = "ID em formato inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "ID inválido", value = ERRO_400_TIPO_ID))),
            @ApiResponse(responseCode = "404", description = "Alimentação não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Alimentação inexistente", value = ERRO_404_ALIMENTACAO)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<AlimentacaoResponse>> buscarPorId(
            @Parameter(description = "ID da alimentação (1 = Ração Tropical, 2 = Artêmia Viva, 3 = Spirulina em Flocos, "
                    + "4 = Larva de Mosquito Congelada)", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(modelo(service.buscarPorId(id)));
    }

    @Operation(summary = "Buscar alimentações por tipo (consulta personalizada)",
            description = """
                    Consulta personalizada `findByTipo`: filtra pelo enum `TipoAlimentacao`.

                    Exemplos: `tipo=RACAO` → Ração Tropical · `tipo=VEGETAL` → Spirulina em Flocos · \
                    `tipo=CONGELADA` → Larva de Mosquito Congelada.""")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT,
                    schema = @Schema(type = "string", example = "nome,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alimentações do tipo informado (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Tipo ausente ou inexistente no enum",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Tipo inválido", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 400,
                                      "erro": "Bad Request",
                                      "mensagem": "Valor 'PEIXE' inválido para o parâmetro 'tipo'.",
                                      "caminho": "/alimentacoes/buscar",
                                      "campos": []
                                    }""")))
    })
    @GetMapping("/buscar")
    public ResponseEntity<PagedModel<EntityModel<AlimentacaoResponse>>> buscarPorTipo(
            @Parameter(description = "Tipo da alimentação", example = "RACAO", required = true)
            @RequestParam TipoAlimentacao tipo,
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(Hateoas.paginar(service.buscarPorTipo(tipo, pageable), this::modelo));
    }

    @Operation(summary = "Cadastrar alimentação (idempotente)",
            description = """
                    Cadastra uma nova alimentação. O nome não pode repetir (senão → 409) e o `tipo` precisa ser um \
                    valor do enum (senão → 400).

                    **Idempotência:** header `X-Idempotency-Key` obrigatório.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alimentação criada. O header Location aponta para ela.",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = AlimentacaoResponse.class),
                            examples = @ExampleObject(name = "Alimentação criada", value = """
                                    {
                                      "id": 5,
                                      "nome": "Krill Congelado",
                                      "tipo": "CONGELADA",
                                      "quantidadePeixes": 0,
                                      "_links": {
                                        "self":         { "href": "http://localhost:8080/alimentacoes/5" },
                                        "update":       { "href": "http://localhost:8080/alimentacoes/5" },
                                        "delete":       { "href": "http://localhost:8080/alimentacoes/5" },
                                        "alimentacoes": { "href": "http://localhost:8080/alimentacoes" }
                                      }
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, tipo fora do enum ou header X-Idempotency-Key ausente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "Tipo inválido", value = ERRO_400_JSON),
                                    @ExampleObject(name = "Sem X-Idempotency-Key", value = ERRO_400_IDEMPOTENCIA)
                            })),
            @ApiResponse(responseCode = "409", description = "Nome duplicado ou chave de idempotência em processamento",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Nome duplicado", value = ERRO_409_ALIMENTACAO_DUPLICADA))),
            @ApiResponse(responseCode = "422", description = "Chave de idempotência já usada com outro corpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave reutilizada", value = ERRO_422_IDEMPOTENCIA)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<AlimentacaoResponse>> criar(
            @Parameter(description = DESC_IDEMPOTENCY_KEY, example = "e5b7c9d1-alimentacao-krill", required = true)
            @RequestHeader(IdempotenciaService.HEADER) String chaveIdempotencia,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Dados da nova alimentação", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AlimentacaoRequest.class),
                            examples = {
                                    @ExampleObject(name = "Krill Congelado", value = """
                                            { "nome": "Krill Congelado", "tipo": "CONGELADA" }"""),
                                    @ExampleObject(name = "Tipo fora do enum (gera 400)", value = """
                                            { "nome": "Minhoca", "tipo": "PEIXE" }""")
                            }))
            @Valid @RequestBody AlimentacaoRequest request) {

        ResultadoIdempotente<AlimentacaoResponse> resultado = idempotencia.executar(
                chaveIdempotencia, "POST /alimentacoes", request,
                () -> service.criar(request), AlimentacaoResponse::id, service::buscarPorId);

        EntityModel<AlimentacaoResponse> modelo = modelo(resultado.resposta());
        return ResponseEntity
                .created(URI.create(modelo.getRequiredLink(IanaLinkRelations.SELF).getHref()))
                .header(IdempotenciaService.HEADER, chaveIdempotencia)
                .header(IdempotenciaService.HEADER_REPETIDA, String.valueOf(resultado.repetida()))
                .body(modelo);
    }

    @Operation(summary = "Atualizar alimentação",
            description = "Atualiza nome e tipo de uma alimentação. Exemplo: `PUT /alimentacoes/1` renomeia a Ração Tropical.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alimentação atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou tipo fora do enum",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Tipo inválido", value = ERRO_400_JSON))),
            @ApiResponse(responseCode = "404", description = "Alimentação não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Alimentação inexistente", value = ERRO_404_ALIMENTACAO))),
            @ApiResponse(responseCode = "409", description = "Outra alimentação já usa esse nome",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Nome duplicado", value = ERRO_409_ALIMENTACAO_DUPLICADA)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<AlimentacaoResponse>> atualizar(
            @Parameter(description = "ID da alimentação a atualizar", example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Novos dados da alimentação", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AlimentacaoRequest.class),
                            examples = @ExampleObject(name = "Ração Tropical Premium", value = """
                                    { "nome": "Ração Tropical Premium", "tipo": "RACAO" }""")))
            @Valid @RequestBody AlimentacaoRequest request) {
        return ResponseEntity.ok(modelo(service.atualizar(id, request)));
    }

    @Operation(summary = "Excluir alimentação",
            description = """
                    Exclui uma alimentação. Por ser um relacionamento Many-to-Many, ela é **retirada da dieta** dos \
                    peixes que a recebiam antes de ser excluída.

                    Exemplo: `DELETE /alimentacoes/4` remove a Larva de Mosquito Congelada (o Azulão fica sem ela). \
                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alimentação excluída"),
            @ApiResponse(responseCode = "404", description = "Alimentação não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Alimentação inexistente", value = ERRO_404_ALIMENTACAO)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID da alimentação a excluir", example = "4") @PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<AlimentacaoResponse> modelo(AlimentacaoResponse alimentacao) {
        return EntityModel.of(alimentacao,
                linkTo(AlimentacaoController.class).slash(alimentacao.id()).withSelfRel(),
                linkTo(AlimentacaoController.class).slash(alimentacao.id()).withRel("update"),
                linkTo(AlimentacaoController.class).slash(alimentacao.id()).withRel("delete"),
                linkTo(AlimentacaoController.class).withRel("alimentacoes"));
    }
}
