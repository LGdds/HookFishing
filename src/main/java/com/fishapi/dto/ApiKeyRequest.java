package com.fishapi.dto;

import com.fishapi.model.PapelAcesso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "ApiKeyRequest", description = "Dados para gerar uma nova API Key")
public record ApiKeyRequest(

        @Schema(description = "Nome do usuário/aplicação dona da chave", example = "Tablet da Equipe de Alimentação",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        String nome,

        @Schema(description = "E-mail de contato do responsável", example = "alimentacao@hookfishing.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O email é obrigatório")
        @Email(message = "Informe um email válido")
        String email,

        @Schema(description = "Papel de acesso: VISITANTE (só consulta), CUIDADOR (cadastra e edita peixes e alimentações), "
                + "BIOLOGO (gerencia peixes, alimentações e espécies), GERENTE (gerencia tudo, inclusive aquários e cuidadores), "
                + "ADMIN (tudo + /api-keys)",
                example = "CUIDADOR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O papel é obrigatório (VISITANTE, CUIDADOR, BIOLOGO, GERENTE ou ADMIN)")
        PapelAcesso papel
) {
}
