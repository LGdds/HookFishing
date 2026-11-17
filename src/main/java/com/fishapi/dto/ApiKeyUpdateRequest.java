package com.fishapi.dto;

import com.fishapi.model.PapelAcesso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "ApiKeyUpdateRequest", description = "Dados para atualizar uma API Key existente")
public record ApiKeyUpdateRequest(

        @Schema(description = "Nome do usuário/aplicação", example = "Equipe de Cuidadores",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        String nome,

        @Schema(description = "E-mail de contato", example = "cuidador@hookfishing.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O email é obrigatório")
        @Email(message = "Informe um email válido")
        String email,

        @Schema(description = "Papel de acesso (VISITANTE, CUIDADOR, BIOLOGO, GERENTE ou ADMIN)", example = "BIOLOGO",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O papel é obrigatório (VISITANTE, CUIDADOR, BIOLOGO, GERENTE ou ADMIN)")
        PapelAcesso papel,

        @Schema(description = "false desativa a chave (requisições com ela passam a receber 401)", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe se a chave está ativa (true/false)")
        Boolean ativa
) {
}
