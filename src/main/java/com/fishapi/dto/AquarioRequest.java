package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "AquarioRequest", description = "Dados para cadastrar ou atualizar um aquário")
public record AquarioRequest(

        @Schema(description = "Nome do aquário (único)", example = "Aquário Quarentena", minLength = 3, maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do aquário é obrigatório")
        @Size(min = 3, max = 100, message = "O nome do aquário deve ter entre 3 e 100 caracteres")
        String nome,

        @Schema(description = "Capacidade em litros", example = "80", minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A capacidade é obrigatória")
        @Min(value = 1, message = "A capacidade deve ser de pelo menos 1 litro")
        Integer capacidadeLitros,

        @Schema(description = "ID do cuidador responsável (opcional). Cada cuidador só pode cuidar de um aquário. "
                + "O cuidador 3 (Carlos Lima) está livre.", example = "3")
        @Positive(message = "O cuidadorId deve ser positivo")
        Long cuidadorId
) {
}
