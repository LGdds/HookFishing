package com.fishapi.dto;

import com.fishapi.seguranca.Hashes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CadastroRequest", description = "Dados para criar uma conta de usuário")
public record CadastroRequest(

        @Schema(description = "Nome completo", example = "Fernanda Rocha", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        String nome,

        @Schema(description = "E-mail (será o login)", example = "fernanda.rocha@email.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Informe um e-mail válido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @Schema(description = "Senha (mínimo de 6 caracteres)", example = "aquario2025", minLength = 6,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres")
        String senha
) {

    /**
     * Evita que a senha apareça em logs: o toString padrão do record mostraria todos os campos.
     * A senha entra apenas como hash, para a idempotência continuar detectando corpos diferentes.
     */
    @Override
    public String toString() {
        return "CadastroRequest[nome=" + nome + ", email=" + email
                + ", senha=" + (senha == null ? null : Hashes.sha256(senha)) + "]";
    }
}
