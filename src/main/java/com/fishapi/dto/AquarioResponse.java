package com.fishapi.dto;

import com.fishapi.model.Aquario;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "aquarios", itemRelation = "aquario")
@Schema(name = "AquarioResponse", description = "Aquário cadastrado")
public record AquarioResponse(
        @Schema(description = "ID do aquário", example = "1") Long id,
        @Schema(description = "Nome do aquário", example = "Aquário Principal") String nome,
        @Schema(description = "Capacidade em litros", example = "200") Integer capacidadeLitros,
        @Schema(description = "Cuidador responsável (null se não houver)") ResumoResponse cuidador,
        @Schema(description = "Quantidade de peixes no aquário", example = "2") Integer quantidadePeixes) {

    public static AquarioResponse de(Aquario a) {
        return new AquarioResponse(
                a.getId(),
                a.getNome(),
                a.getCapacidadeLitros(),
                a.getCuidador() == null ? null : new ResumoResponse(a.getCuidador().getId(), a.getCuidador().getNome()),
                a.getPeixes().size());
    }
}
