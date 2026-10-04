package com.fishapi.controller;

import com.fishapi.model.Aquario;
import com.fishapi.repository.AquarioRepository;

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
@RequestMapping("/aquarios")
@Tag(name = "Aquários", description = "Operações para gerenciamento de aquários")
public class AquarioController {

    private final AquarioRepository repository;

    public AquarioController(AquarioRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Listar aquários",
            description = "Retorna todos os aquários de forma paginada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aquários encontrados"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação inválidos")
    })
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<Aquario>>> listar(
            @ParameterObject Pageable pageable) {

        Page<Aquario> page = repository.findAll(pageable);

        List<EntityModel<Aquario>> aquarios = page.getContent()
                .stream()
                .map(this::adicionarLinks)
                .toList();

        PagedModel.PageMetadata metadata =
                new PagedModel.PageMetadata(
                        page.getSize(),
                        page.getNumber(),
                        page.getTotalElements()
                );

        PagedModel<EntityModel<Aquario>> response =
                PagedModel.of(aquarios, metadata);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Buscar aquário por ID",
            description = "Retorna um aquário específico pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aquário encontrado"),
            @ApiResponse(responseCode = "404", description = "Aquário não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Aquario>> buscarPorId(
            @PathVariable Long id) {

        Optional<Aquario> aquario = repository.findById(id);

        if (aquario.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(aquario.get()));
    }

    @Operation(
            summary = "Cadastrar aquário",
            description = "Cadastra um novo aquário."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aquário criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    @PostMapping
    public ResponseEntity<EntityModel<Aquario>> criar(
            @Valid @RequestBody Aquario aquario) {

        Aquario salvo = repository.save(aquario);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adicionarLinks(salvo));
    }

    @Operation(
            summary = "Atualizar aquário",
            description = "Atualiza um aquário existente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aquário atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Aquário não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<Aquario>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Aquario aquario) {

        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        aquario.setId(id);

        Aquario atualizado = repository.save(aquario);

        return ResponseEntity.ok(adicionarLinks(atualizado));
    }

    @Operation(
            summary = "Excluir aquário",
            description = "Exclui um aquário pelo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Aquário excluído com sucesso"),
            @ApiResponse(responseCode = "404", description = "Aquário não encontrado")
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
            summary = "Buscar aquário por nome",
            description = "Busca um aquário pelo nome."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aquário encontrado"),
            @ApiResponse(responseCode = "404", description = "Aquário não encontrado")
    })
    @GetMapping("/buscar")
    public ResponseEntity<EntityModel<Aquario>> buscarPorNome(
            @RequestParam String nome) {

        Optional<Aquario> aquario =
                repository.findByNomeIgnoreCase(nome);

        if (aquario.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(adicionarLinks(aquario.get()));
    }

    private EntityModel<Aquario> adicionarLinks(Aquario aquario) {

        return EntityModel.of(
                aquario,

                linkTo(methodOn(AquarioController.class)
                        .buscarPorId(aquario.getId()))
                        .withSelfRel(),

                linkTo(methodOn(AquarioController.class)
                        .atualizar(aquario.getId(), aquario))
                        .withRel("update"),

                linkTo(methodOn(AquarioController.class)
                        .deletar(aquario.getId()))
                        .withRel("delete")
        );
    }
}