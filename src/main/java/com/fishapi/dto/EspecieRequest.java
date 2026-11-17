package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "EspecieRequest", description = "Dados para cadastrar ou atualizar uma espécie")
public record EspecieRequest(

        @Schema(description = "Nome popular da espécie (único)", example = "Peixe-anjo", minLength = 3, maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome da espécie é obrigatório")
        @Size(min = 3, max = 100, message = "O nome da espécie deve ter entre 3 e 100 caracteres")
        String nome,

        @Schema(description = "Família biológica", example = "Pomacanthidae", minLength = 3, maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A família é obrigatória")
        @Size(min = 3, max = 100, message = "A família deve ter entre 3 e 100 caracteres")
        String familia
) {
}
