package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credenciais de acesso")
public record LoginRequest(

        @Schema(description = "E-mail cadastrado", example = "cuidador@hookfishing.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O e-mail é obrigatório")
        String email,

        @Schema(description = "Senha", example = "cuidador123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A senha é obrigatória")
        String senha
) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
