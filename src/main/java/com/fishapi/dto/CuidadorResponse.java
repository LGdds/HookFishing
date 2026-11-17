package com.fishapi.dto;

import com.fishapi.model.Cuidador;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "cuidadores", itemRelation = "cuidador")
@Schema(name = "CuidadorResponse", description = "Cuidador cadastrado")
public record CuidadorResponse(
        @Schema(description = "ID do cuidador", example = "1") Long id,
        @Schema(description = "Nome completo", example = "João Silva") String nome,
        @Schema(description = "E-mail", example = "joao@email.com") String email,
        @Schema(description = "Aquário sob responsabilidade do cuidador (null se não houver)") ResumoResponse aquario) {

    public static CuidadorResponse de(Cuidador c) {
        return new CuidadorResponse(
                c.getId(),
                c.getNome(),
                c.getEmail(),
                c.getAquario() == null ? null : new ResumoResponse(c.getAquario().getId(), c.getAquario().getNome()));
    }
}
