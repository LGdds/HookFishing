package com.fishapi.controller;

import com.fishapi.model.Cuidador;
import com.fishapi.repository.CuidadorRepository;

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
@RequestMapping("/cuidadores")
@Tag(name = "Cuidadores", description = "Operações para gerenciamento de cuidadores")
public class CuidadorController {

    private final CuidadorRepository repository;

    public CuidadorController(CuidadorRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Listar cuidadores",
            description = "Retorna todos os cuidadores de forma paginada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuidadores encontrados"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação inválidos")
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Cuidador>>> listar(
            @ParameterObject Pageable pageable) {

        Page<Cuidador> page = repository.findAll(pageable);

        List<EntityModel<Cuidador>> cuidadores = page.getContent()
                .stream()
                .map(this::adicionarLinks)
                .toList();

        PagedModel.PageMetadata metadata =
                new PagedModel.PageMetadata(
                        page.getSize(),
                        page.getNumber(),
                        page.getTotalElements()
                );

        PagedModel<EntityModel<Cuidador>> response =
                PagedModel.of(cuidadores, metadata);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Buscar cuidador por ID",
            description = "Retorna um cuidador específico pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuidador encontrado"),
            @ApiResponse(responseCode = "404", description = "Cuidador não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Cuidador>> buscarPorId(
            @PathVariable Long id) {

        Optional<Cuidador> cuidador = repository.findById(id);

        if (cuidador.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(cuidador.get()));
    }

    @Operation(
            summary = "Cadastrar cuidador",
            description = "Cadastra um novo cuidador."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuidador criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    @PostMapping
    public ResponseEntity<EntityModel<Cuidador>> criar(
            @Valid @RequestBody Cuidador cuidador) {

        Cuidador salvo = repository.save(cuidador);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adicionarLinks(salvo));
    }

    @Operation(
            summary = "Atualizar cuidador",
            description = "Atualiza um cuidador existente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuidador atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Cuidador não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Cuidador>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Cuidador cuidador) {

        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        cuidador.setId(id);

        Cuidador atualizado = repository.save(cuidador);

        return ResponseEntity.ok(adicionarLinks(atualizado));
    }

    @Operation(
            summary = "Excluir cuidador",
            description = "Exclui um cuidador pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cuidador excluído com sucesso"),
            @ApiResponse(responseCode = "404", description = "Cuidador não encontrado")
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
            summary = "Buscar cuidador por e-mail",
            description = "Busca um cuidador pelo endereço de e-mail."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuidador encontrado"),
            @ApiResponse(responseCode = "404", description = "Cuidador não encontrado")
    })
    @GetMapping("/buscar")
    public ResponseEntity<EntityModel<Cuidador>> buscarPorEmail(
            @RequestParam String email) {

        Optional<Cuidador> cuidador =
                repository.findByEmailIgnoreCase(email);

        if (cuidador.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(cuidador.get()));
    }

    private EntityModel<Cuidador> adicionarLinks(Cuidador cuidador) {

        return EntityModel.of(
                cuidador,

                linkTo(methodOn(CuidadorController.class)
                        .buscarPorId(cuidador.getId()))
                        .withSelfRel(),

                linkTo(methodOn(CuidadorController.class)
                        .atualizar(cuidador.getId(), cuidador))
                        .withRel("update"),

                linkTo(methodOn(CuidadorController.class)
                        .deletar(cuidador.getId()))
                        .withRel("delete")
        );
    }
}