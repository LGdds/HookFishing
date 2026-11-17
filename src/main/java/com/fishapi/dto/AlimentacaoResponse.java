package com.fishapi.dto;

import com.fishapi.model.Alimentacao;
import com.fishapi.model.TipoAlimentacao;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "alimentacoes", itemRelation = "alimentacao")
@Schema(name = "AlimentacaoResponse", description = "Alimentação cadastrada")
public record AlimentacaoResponse(
        @Schema(description = "ID da alimentação", example = "1") Long id,
        @Schema(description = "Nome", example = "Ração Tropical") String nome,
        @Schema(description = "Tipo (enum)", example = "RACAO") TipoAlimentacao tipo,
        @Schema(description = "Quantidade de peixes que recebem esta alimentação", example = "4") Integer quantidadePeixes) {

    public static AlimentacaoResponse de(Alimentacao a) {
        return new AlimentacaoResponse(a.getId(), a.getNome(), a.getTipo(), a.getPeixes().size());
    }
}
