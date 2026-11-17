package com.fishapi.dto;

import com.fishapi.model.Especie;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "especies", itemRelation = "especie")
@Schema(name = "EspecieResponse", description = "Espécie cadastrada")
public record EspecieResponse(
        @Schema(description = "ID da espécie", example = "1") Long id,
        @Schema(description = "Nome popular", example = "Peixe-palhaço") String nome,
        @Schema(description = "Família biológica", example = "Pomacentridae") String familia,
        @Schema(description = "Quantidade de peixes desta espécie", example = "2") Integer quantidadePeixes) {

    public static EspecieResponse de(Especie e) {
        return new EspecieResponse(e.getId(), e.getNome(), e.getFamilia(), e.getPeixes().size());
    }
}
