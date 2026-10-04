package com.fishapi.controller;

import com.fishapi.model.Alimentacao;
import com.fishapi.repository.AlimentacaoRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springdoc.core.annotations.ParameterObject;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Optional;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
@RequestMapping("/alimentacoes")
@Tag(name = "Alimentações", description = "Operações para gerenciamento de alimentações")
public class AlimentacaoController {

    private final AlimentacaoRepository repository;

    public AlimentacaoController(AlimentacaoRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Listar alimentações",
            description = "Retorna todas as alimentações de forma paginada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alimentações encontradas"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação inválidos")
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Alimentacao>>> listar(
            @ParameterObject Pageable pageable) {

        Page<Alimentacao> page = repository.findAll(pageable);

        List<EntityModel<Alimentacao>> alimentacoes = page.getContent()
                .stream()
                .map(this::adicionarLinks)
                .toList();

        PagedModel.PageMetadata metadata =
                new PagedModel.PageMetadata(
                        page.getSize(),
                        page.getNumber(),
                        page.getTotalElements()
                );

        PagedModel<EntityModel<Alimentacao>> response =
                PagedModel.of(alimentacoes, metadata);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Buscar alimentação por ID",
            description = "Retorna uma alimentação específica pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alimentação encontrada"),
            @ApiResponse(responseCode = "404", description = "Alimentação não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Alimentacao>> buscarPorId(
            @PathVariable Long id) {

        Optional<Alimentacao> alimentacao =
                repository.findById(id);

        if (alimentacao.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(alimentacao.get()));
    }

    @Operation(
            summary = "Cadastrar alimentação",
            description = "Cadastra uma nova alimentação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alimentação criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    @PostMapping
    public ResponseEntity<EntityModel<Alimentacao>> criar(
            @Valid @RequestBody Alimentacao alimentacao) {

        Alimentacao salva = repository.save(alimentacao);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adicionarLinks(salva));
    }

    @Operation(
            summary = "Atualizar alimentação",
            description = "Atualiza uma alimentação existente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alimentação atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Alimentação não encontrada")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Alimentacao>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Alimentacao alimentacao) {

        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        alimentacao.setId(id);

        Alimentacao atualizada =
                repository.save(alimentacao);

        return ResponseEntity.ok(adicionarLinks(atualizada));
    }

    @Operation(
            summary = "Excluir alimentação",
            description = "Exclui uma alimentação pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alimentação excluída com sucesso"),
            @ApiResponse(responseCode = "404", description = "Alimentação não encontrada")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @PathVariable Long id) {

        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        repository.deleteById(id);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Buscar alimentação por nome",
            description = "Busca uma alimentação pelo nome."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alimentação encontrada"),
            @ApiResponse(responseCode = "404", description = "Alimentação não encontrada")
    })
    @GetMapping("/buscar")
    public ResponseEntity<EntityModel<Alimentacao>> buscarPorNome(
            @RequestParam String nome) {

        Optional<Alimentacao> alimentacao =
                repository.findByNomeIgnoreCase(nome);

        if (alimentacao.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(alimentacao.get()));
    }

    private EntityModel<Alimentacao> adicionarLinks(
            Alimentacao alimentacao) {

        return EntityModel.of(
                alimentacao,

                linkTo(methodOn(AlimentacaoController.class)
                        .buscarPorId(alimentacao.getId()))
                        .withSelfRel(),

                linkTo(methodOn(AlimentacaoController.class)
                        .atualizar(alimentacao.getId(), alimentacao))
                        .withRel("update"),

                linkTo(methodOn(AlimentacaoController.class)
                        .deletar(alimentacao.getId()))
                        .withRel("delete")
        );
    }
}