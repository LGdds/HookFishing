package com.fishapi.controller;

import com.fishapi.dto.PeixeRequest;
import com.fishapi.dto.PeixeResponse;
import com.fishapi.dto.PeixeV2Response;
import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.idempotencia.ResultadoIdempotente;
import com.fishapi.exception.ErroResponse;
import com.fishapi.service.PeixeService;
import com.fishapi.versionamento.ApiVersionInterceptor;

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
@RequestMapping("/peixes")
@Tag(name = "Peixes", description = """
        Cadastro dos **peixes** do aquário. Cada peixe pertence a uma **espécie** e a um **aquário** \
        (One-to-Many) e pode receber várias **alimentações** (Many-to-Many).

        Esta é a aba que demonstra o **versionamento** (`X-API-Version: 1` ou `2`).

        **Exemplos do que dá para fazer aqui:**
        - `GET /peixes?page=0&size=5&sort=nome,asc`: lista os peixes em ordem alfabética
        - `GET /peixes/1` com `X-API-Version: 2`: mostra o Nemo com espécie, aquário e alimentações detalhados
        - `GET /peixes/buscar?nome=nemo`: encontra o Nemo (sem diferenciar maiúsculas e minúsculas)
        - `POST /peixes`: cadastra a Coral (Peixe-palhaço) no Aquário Principal
        - `PUT /peixes/1`: transfere o Nemo para o Aquário Recifal
        - `DELETE /peixes/2`: remove o Marlin
        """)
public class PeixeController {

    private static final String SORT_PEIXE = "Ordenação no formato `campo,asc|desc`. Campos: `id`, `nome`, `idade`, `tamanho`.";

    private final PeixeService service;
    private final IdempotenciaService idempotencia;

    public PeixeController(PeixeService service, IdempotenciaService idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    // ---------------------------------------------------------------- LISTAR (v1 e v2)

    @Operation(
            summary = "Listar peixes (paginado, versionado)",
            description = """
                    Retorna os peixes de forma paginada, com links HATEOAS de navegação (`first`, `prev`, `next`, `last`).

                    **Versionamento:** envie `X-API-Version: 2` para receber espécie, aquário e alimentações detalhados. \
                    Sem o header (ou com `1`), os relacionamentos vêm apenas como IDs.

                    Exemplo: `GET /peixes?page=0&size=2&sort=nome,asc` → Azulão, Dory (página 1 de 3).""")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "5")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT_PEIXE,
                    schema = @Schema(type = "string", example = "nome,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de peixes. O formato depende da versão (v1 ou v2).",
                    content = @Content(mediaType = "application/hal+json", examples = {
                            @ExampleObject(name = "Versão 1 (padrão)", value = PEIXES_PAGINA_V1),
                            @ExampleObject(name = "Versão 2 (X-API-Version: 2)", value = PEIXES_PAGINA_V2)
                    })),
            @ApiResponse(responseCode = "400", description = "Campo de ordenação inválido ou versão da API não suportada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "Ordenação inválida", value = ERRO_400_ORDENACAO),
                                    @ExampleObject(name = "Versão inválida", value = ERRO_400_VERSAO)
                            }))
    })
    @GetMapping
    public ResponseEntity<PagedModel<?>> listar(
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @Parameter(name = ApiVersionInterceptor.HEADER, in = ParameterIn.HEADER, description = DESC_VERSAO,
                    schema = @Schema(type = "string", allowableValues = {"1", "2"}, defaultValue = "1", example = "2"))
            @RequestHeader(value = ApiVersionInterceptor.HEADER, required = false) String versao) {

        if ("2".equals(ApiVersionInterceptor.resolver(versao))) {
            return ResponseEntity.ok(Hateoas.paginar(service.listarV2(pageable), this::modeloV2));
        }
        return ResponseEntity.ok(Hateoas.paginar(service.listar(pageable), this::modelo));
    }

    // ---------------------------------------------------------------- BUSCAR POR ID (v1 e v2)

    @Operation(
            summary = "Buscar peixe por ID (versionado)",
            description = """
                    Retorna um peixe pelo ID, com links para atualizar, excluir e navegar até a espécie e o aquário.

                    **Teste o versionamento:** `GET /peixes/1` sem header devolve o Nemo com `especieId: 1`; \
                    com `X-API-Version: 2` devolve `especie: { "nome": "Peixe-palhaço", ... }`, `faixaEtaria: "JOVEM"` etc.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peixe encontrado. O formato depende da versão (v1 ou v2).",
                    content = @Content(mediaType = "application/hal+json", examples = {
                            @ExampleObject(name = "Versão 1 (padrão)", value = PEIXE_V1),
                            @ExampleObject(name = "Versão 2 (X-API-Version: 2)", value = PEIXE_V2)
                    })),
            @ApiResponse(responseCode = "400", description = "ID em formato inválido ou versão não suportada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "Versão inválida", value = ERRO_400_VERSAO),
                                    @ExampleObject(name = "ID inválido", value = ERRO_400_TIPO_ID)
                            })),
            @ApiResponse(responseCode = "404", description = "Peixe não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Peixe inexistente", value = ERRO_404_PEIXE)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<?>> buscarPorId(
            @Parameter(description = "ID do peixe (1 = Nemo, 2 = Marlin, 3 = Dory, 4 = Azulão, 5 = Faísca)", example = "1")
            @PathVariable Long id,
            @Parameter(name = ApiVersionInterceptor.HEADER, in = ParameterIn.HEADER, description = DESC_VERSAO,
                    schema = @Schema(type = "string", allowableValues = {"1", "2"}, defaultValue = "1", example = "2"))
            @RequestHeader(value = ApiVersionInterceptor.HEADER, required = false) String versao) {

        if ("2".equals(ApiVersionInterceptor.resolver(versao))) {
            return ResponseEntity.ok(modeloV2(service.buscarPorIdV2(id)));
        }
        return ResponseEntity.ok(modelo(service.buscarPorId(id)));
    }

    // ---------------------------------------------------------------- BUSCA PERSONALIZADA

    @Operation(
            summary = "Buscar peixes por nome (consulta personalizada)",
            description = """
                    Consulta personalizada `findByNomeContainingIgnoreCase`: retorna, de forma paginada, os peixes cujo \
                    nome **contém** o texto informado, sem diferenciar maiúsculas de minúsculas.

                    Exemplos: `nome=nemo` → Nemo · `nome=ar` → Marlin · `nome=a` → Marlin, Dory, Azulão, Faísca.""")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = DESC_PAGE,
                    schema = @Schema(type = "integer", defaultValue = "0", example = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = DESC_SIZE,
                    schema = @Schema(type = "integer", defaultValue = "10", example = "10")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = SORT_PEIXE,
                    schema = @Schema(type = "string", example = "nome,asc"))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peixes encontrados (a lista pode vir vazia)"),
            @ApiResponse(responseCode = "400", description = "Parâmetro 'nome' ausente/vazio ou ordenação inválida",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Ordenação inválida", value = ERRO_400_ORDENACAO)))
    })
    @GetMapping("/buscar")
    public ResponseEntity<PagedModel<EntityModel<PeixeResponse>>> buscarPorNome(
            @Parameter(description = "Parte do nome do peixe", example = "nemo", required = true)
            @RequestParam String nome,
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "nome") Pageable pageable) {

        return ResponseEntity.ok(Hateoas.paginar(service.buscarPorNome(nome, pageable), this::modelo));
    }

    // ---------------------------------------------------------------- CRIAR (idempotente)

    @Operation(
            summary = "Cadastrar peixe (idempotente)",
            description = """
                    Cadastra um novo peixe vinculado a uma espécie, a um aquário e (opcionalmente) a alimentações.

                    **Idempotência:** o header `X-Idempotency-Key` é obrigatório. Clique em *Execute* duas vezes com a \
                    mesma chave e o mesmo corpo: a segunda resposta devolve o **mesmo** peixe, com \
                    `X-Idempotent-Replayed: true`, e nada é duplicado. Para cadastrar outro peixe, troque a chave.

                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Peixe criado (ou devolvido novamente, se a chave já tiver sido usada com o mesmo corpo). O header Location aponta para o novo recurso.",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = PeixeResponse.class),
                            examples = @ExampleObject(name = "Peixe criado", value = """
                                    {
                                      "id": 6,
                                      "nome": "Coral",
                                      "idade": 1,
                                      "tamanho": 7.0,
                                      "especieId": 1,
                                      "aquarioId": 1,
                                      "alimentacaoIds": [1, 2],
                                      "_links": {
                                        "self":    { "href": "http://localhost:8080/peixes/6" },
                                        "update":  { "href": "http://localhost:8080/peixes/6" },
                                        "delete":  { "href": "http://localhost:8080/peixes/6" },
                                        "especie": { "href": "http://localhost:8080/especies/1" },
                                        "aquario": { "href": "http://localhost:8080/aquarios/1" },
                                        "peixes":  { "href": "http://localhost:8080/peixes" }
                                      }
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, JSON mal formatado ou header X-Idempotency-Key ausente/inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "Campos inválidos", value = ERRO_400_VALIDACAO),
                                    @ExampleObject(name = "Sem X-Idempotency-Key", value = ERRO_400_IDEMPOTENCIA)
                            })),
            @ApiResponse(responseCode = "404", description = "Espécie, aquário ou alimentação informados não existem",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Espécie inexistente", value = ERRO_404_RELACIONAMENTO))),
            @ApiResponse(responseCode = "409", description = "Requisição com a mesma chave de idempotência ainda em processamento",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave em processamento", value = ERRO_409_IDEMPOTENCIA))),
            @ApiResponse(responseCode = "422", description = "Chave de idempotência já usada com outro corpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave reutilizada", value = ERRO_422_IDEMPOTENCIA)))
    })
    @PostMapping
    public ResponseEntity<EntityModel<PeixeResponse>> criar(
            @Parameter(description = DESC_IDEMPOTENCY_KEY, example = IDEMPOTENCY_KEY, required = true)
            @RequestHeader(IdempotenciaService.HEADER) String chaveIdempotencia,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Dados do novo peixe. Use IDs que já existem na base (veja a descrição de cada campo).",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PeixeRequest.class),
                            examples = {
                                    @ExampleObject(name = "Coral no Aquário Principal", value = """
                                            {
                                              "nome": "Coral",
                                              "idade": 1,
                                              "tamanho": 7.0,
                                              "especieId": 1,
                                              "aquarioId": 1,
                                              "alimentacaoIds": [1, 2]
                                            }"""),
                                    @ExampleObject(name = "Betta no Aquário Comunitário", value = """
                                            {
                                              "nome": "Rubi",
                                              "idade": 0,
                                              "tamanho": 4.5,
                                              "especieId": 3,
                                              "aquarioId": 3,
                                              "alimentacaoIds": [4]
                                            }"""),
                                    @ExampleObject(name = "Inválido (gera 400)", value = """
                                            {
                                              "nome": "",
                                              "idade": -1,
                                              "tamanho": 0,
                                              "especieId": 1,
                                              "aquarioId": 1
                                            }""")
                            }))
            @Valid @RequestBody PeixeRequest request) {

        ResultadoIdempotente<PeixeResponse> resultado = idempotencia.executar(
                chaveIdempotencia, "POST /peixes", request,
                () -> service.criar(request),
                PeixeResponse::id,
                service::buscarPorId);

        EntityModel<PeixeResponse> modelo = modelo(resultado.resposta());
        return ResponseEntity
                .created(URI.create(modelo.getRequiredLink(IanaLinkRelations.SELF).getHref()))
                .header(IdempotenciaService.HEADER, chaveIdempotencia)
                .header(IdempotenciaService.HEADER_REPETIDA, String.valueOf(resultado.repetida()))
                .body(modelo);
    }

    // ---------------------------------------------------------------- ATUALIZAR

    @Operation(
            summary = "Atualizar peixe",
            description = """
                    Substitui todos os dados de um peixe existente (PUT), inclusive espécie, aquário e alimentações.

                    Exemplo: `PUT /peixes/1` com o corpo abaixo transfere o Nemo para o Aquário Recifal e adiciona Spirulina à dieta.

                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peixe atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou JSON mal formatado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Campos inválidos", value = ERRO_400_VALIDACAO))),
            @ApiResponse(responseCode = "404", description = "Peixe, espécie, aquário ou alimentação não encontrados",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "Peixe inexistente", value = ERRO_404_PEIXE),
                                    @ExampleObject(name = "Espécie inexistente", value = ERRO_404_RELACIONAMENTO)
                            }))
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<PeixeResponse>> atualizar(
            @Parameter(description = "ID do peixe a atualizar", example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Novos dados do peixe",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PeixeRequest.class),
                            examples = @ExampleObject(name = "Nemo vai para o Aquário Recifal", value = """
                                    {
                                      "nome": "Nemo",
                                      "idade": 3,
                                      "tamanho": 9.0,
                                      "especieId": 1,
                                      "aquarioId": 2,
                                      "alimentacaoIds": [1, 2, 3]
                                    }""")))
            @Valid @RequestBody PeixeRequest request) {

        return ResponseEntity.ok(modelo(service.atualizar(id, request)));
    }

    // ---------------------------------------------------------------- EXCLUIR

    @Operation(
            summary = "Excluir peixe",
            description = """
                    Remove um peixe e seus vínculos com alimentações (tabela `peixe_alimentacao`).

                    Exemplo: `DELETE /peixes/2` remove o Marlin.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Peixe excluído (sem corpo na resposta)"),
            @ApiResponse(responseCode = "400", description = "ID em formato inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "ID inválido", value = ERRO_400_TIPO_ID))),
            @ApiResponse(responseCode = "404", description = "Peixe não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Peixe inexistente", value = ERRO_404_PEIXE)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID do peixe a excluir", example = "2") @PathVariable Long id) {

        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- HATEOAS

    private EntityModel<PeixeResponse> modelo(PeixeResponse peixe) {
        EntityModel<PeixeResponse> modelo = EntityModel.of(peixe);
        adicionarLinks(modelo, peixe.id(), peixe.especieId(), peixe.aquarioId());
        return modelo;
    }

    private EntityModel<PeixeV2Response> modeloV2(PeixeV2Response peixe) {
        EntityModel<PeixeV2Response> modelo = EntityModel.of(peixe);
        adicionarLinks(modelo, peixe.id(),
                peixe.especie() == null ? null : peixe.especie().id(),
                peixe.aquario() == null ? null : peixe.aquario().id());
        return modelo;
    }

    private void adicionarLinks(EntityModel<?> modelo, Long id, Long especieId, Long aquarioId) {
        modelo.add(linkTo(PeixeController.class).slash(id).withSelfRel());
        modelo.add(linkTo(PeixeController.class).slash(id).withRel("update"));
        modelo.add(linkTo(PeixeController.class).slash(id).withRel("delete"));
        if (especieId != null) {
            modelo.add(linkTo(EspecieController.class).slash(especieId).withRel("especie"));
        }
        if (aquarioId != null) {
            modelo.add(linkTo(AquarioController.class).slash(aquarioId).withRel("aquario"));
        }
        modelo.add(linkTo(PeixeController.class).withRel("peixes"));
    }
}
