package com.fishapi.dto;

import com.fishapi.model.Alimentacao;
import com.fishapi.model.Peixe;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

import java.util.List;

/** Representação do peixe na versão 1 da API (relacionamentos apenas por ID). */
@Relation(collectionRelation = "peixes", itemRelation = "peixe")
@Schema(name = "PeixeResponseV1", description = "Peixe no formato da versão 1: relacionamentos representados apenas por IDs")
public record PeixeResponse(
        @Schema(description = "ID do peixe", example = "1") Long id,
        @Schema(description = "Nome do peixe", example = "Nemo") String nome,
        @Schema(description = "Idade em anos", example = "2") Integer idade,
        @Schema(description = "Tamanho em centímetros", example = "8.5") Double tamanho,
        @Schema(description = "ID da espécie", example = "1") Long especieId,
        @Schema(description = "ID do aquário", example = "1") Long aquarioId,
        @ArraySchema(arraySchema = @Schema(description = "IDs das alimentações", example = "[1, 2]"),
                schema = @Schema(type = "integer", format = "int64", example = "1"))
        List<Long> alimentacaoIds) {

    public static PeixeResponse de(Peixe p) {
        return new PeixeResponse(
                p.getId(),
                p.getNome(),
                p.getIdade(),
                p.getTamanho(),
                p.getEspecie() == null ? null : p.getEspecie().getId(),
                p.getAquario() == null ? null : p.getAquario().getId(),
                p.getAlimentacoes().stream().map(Alimentacao::getId).sorted().toList());
    }
}
