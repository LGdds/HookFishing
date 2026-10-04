package com.fishapi.controller;

import com.fishapi.model.Especie;
import com.fishapi.repository.EspecieRepository;

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
@RequestMapping("/especies")
@Tag(name = "Espécies", description = "Operações para gerenciamento de espécies")
public class EspecieController {

    private final EspecieRepository repository;

    public EspecieController(EspecieRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Listar espécies",
            description = "Retorna todas as espécies de forma paginada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espécies encontradas"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação inválidos")
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Especie>>> listar(
            @ParameterObject Pageable pageable) {

        Page<Especie> page = repository.findAll(pageable);

        List<EntityModel<Especie>> especies = page.getContent()
                .stream()
                .map(this::adicionarLinks)
                .toList();

        PagedModel.PageMetadata metadata =
                new PagedModel.PageMetadata(
                        page.getSize(),
                        page.getNumber(),
                        page.getTotalElements()
                );

        PagedModel<EntityModel<Especie>> response =
                PagedModel.of(especies, metadata);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Buscar espécie por ID",
            description = "Retorna uma espécie específica pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espécie encontrada"),
            @ApiResponse(responseCode = "404", description = "Espécie não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Especie>> buscarPorId(
            @PathVariable Long id) {

        Optional<Especie> especie = repository.findById(id);

        if (especie.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(especie.get()));
    }

    @Operation(
            summary = "Cadastrar espécie",
            description = "Cadastra uma nova espécie."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Espécie criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    @PostMapping
    public ResponseEntity<EntityModel<Especie>> criar(
            @Valid @RequestBody Especie especie) {

        Especie salva = repository.save(especie);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adicionarLinks(salva));
    }

    @Operation(
            summary = "Atualizar espécie",
            description = "Atualiza uma espécie existente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espécie atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Espécie não encontrada")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Especie>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Especie especie) {

        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        especie.setId(id);

        Especie atualizada = repository.save(especie);

        return ResponseEntity.ok(adicionarLinks(atualizada));
    }

    @Operation(
            summary = "Excluir espécie",
            description = "Exclui uma espécie pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Espécie excluída com sucesso"),
            @ApiResponse(responseCode = "404", description = "Espécie não encontrada")
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
            summary = "Buscar espécie por nome",
            description = "Busca uma espécie pelo nome."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espécie encontrada"),
            @ApiResponse(responseCode = "404", description = "Espécie não encontrada")
    })
    @GetMapping("/buscar")
    public ResponseEntity<EntityModel<Especie>> buscarPorNome(
            @RequestParam String nome) {

        Optional<Especie> especie =
                repository.findByNomeIgnoreCase(nome);

        if (especie.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(especie.get()));
    }

    private EntityModel<Especie> adicionarLinks(Especie especie) {

        return EntityModel.of(
                especie,

                linkTo(methodOn(EspecieController.class)
                        .buscarPorId(especie.getId()))
                        .withSelfRel(),

                linkTo(methodOn(EspecieController.class)
                        .atualizar(especie.getId(), especie))
                        .withRel("update"),

                linkTo(methodOn(EspecieController.class)
                        .deletar(especie.getId()))
                        .withRel("delete")
        );
    }
}