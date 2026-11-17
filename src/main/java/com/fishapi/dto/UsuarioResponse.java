package com.fishapi.dto;

import com.fishapi.model.PapelAcesso;
import com.fishapi.model.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UsuarioResponse", description = "Usuário cadastrado")
public record UsuarioResponse(
        @Schema(description = "ID do usuário", example = "6") Long id,
        @Schema(description = "Nome", example = "Fernanda Rocha") String nome,
        @Schema(description = "E-mail", example = "fernanda.rocha@email.com") String email,
        @Schema(description = "Papel atual (contas novas começam como VISITANTE; um ADMIN pode promover)",
                example = "VISITANTE") PapelAcesso papel,
        @Schema(description = "ID da API Key do usuário (gerenciável em /api-keys)", example = "11") Long apiKeyId,
        @Schema(description = "Data de cadastro (UTC)", example = "2026-11-12T22:30:00Z") String criadoEm) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getApiKey().getPapel(),
                u.getApiKey().getId(),
                u.getCriadoEm() == null ? null : u.getCriadoEm().toString());
    }
}
