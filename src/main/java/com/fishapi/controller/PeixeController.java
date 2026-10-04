package com.fishapi.controller;

import com.fishapi.model.Peixe;
import com.fishapi.repository.PeixeRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping("/peixes")
@Tag(name = "Peixes", description = "Operações para gerenciamento de peixes")
public class PeixeController {

    private final PeixeRepository repository;

    public PeixeController(PeixeRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Listar peixes",
            description = "Retorna todos os peixes de forma paginada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peixes encontrados"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação inválidos")
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Peixe>>> listar(
            @ParameterObject Pageable pageable) {

        Page<Peixe> page = repository.findAll(pageable);

        List<EntityModel<Peixe>> peixes = page.getContent()
                .stream()
                .map(this::adicionarLinks)
                .toList();

        PagedModel.PageMetadata metadata =
                new PagedModel.PageMetadata(
                        page.getSize(),
                        page.getNumber(),
                        page.getTotalElements()
                );

        PagedModel<EntityModel<Peixe>> response =
                PagedModel.of(peixes, metadata);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Buscar peixe por ID",
            description = "Retorna um peixe específico pelo seu ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peixe encontrado"),
            @ApiResponse(responseCode = "404", description = "Peixe não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Peixe>> buscarPorId(
            @PathVariable Long id) {

        Optional<Peixe> peixe = repository.findById(id);

        if (peixe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(peixe.get()));
    }

    @Operation(
            summary = "Cadastrar peixe",
            description = "Cadastra um novo peixe."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Peixe criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    @PostMapping
    public ResponseEntity<EntityModel<Peixe>> criar(
            @Valid @RequestBody Peixe peixe) {

        Peixe salvo = repository.save(peixe);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adicionarLinks(salvo));
    }

    @Operation(
            summary = "Atualizar peixe",
            description = "Atualiza os dados de um peixe existente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peixe atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Peixe não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Peixe>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Peixe peixe) {

        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        peixe.setId(id);

        Peixe atualizado = repository.save(peixe);

        return ResponseEntity.ok(adicionarLinks(atualizado));
    }

    @Operation(
            summary = "Excluir peixe",
            description = "Exclui um peixe pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Peixe excluído com sucesso"),
            @ApiResponse(responseCode = "404", description = "Peixe não encontrado")
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
            summary = "Buscar peixe por nome",
            description = "Realiza uma busca personalizada pelo nome do peixe."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peixe encontrado"),
            @ApiResponse(responseCode = "404", description = "Peixe não encontrado")
    })
    @GetMapping("/buscar")
    public ResponseEntity<EntityModel<Peixe>> buscarPorNome(
            @RequestParam String nome) {

        Optional<Peixe> peixe =
                repository.findByNomeIgnoreCase(nome);

        if (peixe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(peixe.get()));
    }

    private EntityModel<Peixe> adicionarLinks(Peixe peixe) {

        return EntityModel.of(
                peixe,

                linkTo(methodOn(PeixeController.class)
                        .buscarPorId(peixe.getId()))
                        .withSelfRel(),

                linkTo(methodOn(PeixeController.class)
                        .atualizar(peixe.getId(), peixe))
                        .withRel("update"),

                linkTo(methodOn(PeixeController.class)
                        .deletar(peixe.getId()))
                        .withRel("delete")
        );
    }
}