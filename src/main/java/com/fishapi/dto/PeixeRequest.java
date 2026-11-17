package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(name = "PeixeRequest", description = "Dados para cadastrar ou atualizar um peixe")
public record PeixeRequest(

        @Schema(description = "Nome do peixe", example = "Coral", minLength = 2, maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do peixe é obrigatório")
        @Size(min = 2, max = 100, message = "O nome do peixe deve ter entre 2 e 100 caracteres")
        String nome,

        @Schema(description = "Idade em anos", example = "1", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A idade é obrigatória")
        @Min(value = 0, message = "A idade não pode ser negativa")
        Integer idade,

        @Schema(description = "Tamanho em centímetros", example = "7.0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O tamanho é obrigatório")
        @Positive(message = "O tamanho deve ser maior que zero")
        Double tamanho,

        @Schema(description = "ID da espécie (1 = Peixe-palhaço, 2 = Cirurgião-patela, 3 = Betta, 4 = Tetra-neon)",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A espécie é obrigatória (informe especieId)")
        @Positive(message = "O especieId deve ser positivo")
        Long especieId,

        @Schema(description = "ID do aquário (1 = Aquário Principal, 2 = Aquário Recifal, 3 = Aquário Comunitário)",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O aquário é obrigatório (informe aquarioId)")
        @Positive(message = "O aquarioId deve ser positivo")
        Long aquarioId,

        @ArraySchema(
                arraySchema = @Schema(description = "IDs das alimentações do peixe (1 = Ração Tropical, 2 = Artêmia Viva, "
                        + "3 = Spirulina em Flocos, 4 = Larva de Mosquito Congelada)", example = "[1, 2]"),
                schema = @Schema(type = "integer", format = "int64", example = "1"))
        List<@NotNull(message = "IDs de alimentação não podem ser nulos")
             @Positive(message = "IDs de alimentação devem ser positivos") Long> alimentacaoIds
) {
}
