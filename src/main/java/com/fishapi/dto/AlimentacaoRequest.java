package com.fishapi.dto;

import com.fishapi.model.TipoAlimentacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AlimentacaoRequest", description = "Dados para cadastrar ou atualizar uma alimentação")
public record AlimentacaoRequest(

        @Schema(description = "Nome da alimentação (único)", example = "Krill Congelado", minLength = 3, maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome da alimentação é obrigatório")
        @Size(min = 3, max = 100, message = "O nome da alimentação deve ter entre 3 e 100 caracteres")
        String nome,

        @Schema(description = "Tipo da alimentação (enum)", example = "CONGELADA",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O tipo de alimentação é obrigatório")
        TipoAlimentacao tipo
) {
}
