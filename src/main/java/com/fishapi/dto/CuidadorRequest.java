package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CuidadorRequest", description = "Dados para cadastrar ou atualizar um cuidador")
public record CuidadorRequest(

        @Schema(description = "Nome completo", example = "Marina Costa", minLength = 3, maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        String nome,

        @Schema(description = "E-mail (único)", example = "marina.costa@email.com", format = "email",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O email é obrigatório")
        @Email(message = "Informe um email válido")
        @Size(max = 150, message = "O email deve ter no máximo 150 caracteres")
        String email
) {
}
