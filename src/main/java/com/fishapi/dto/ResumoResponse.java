package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Referência resumida a outro recurso (id + nome). */
@Schema(name = "Resumo", description = "Referência resumida a um recurso relacionado")
public record ResumoResponse(
        @Schema(description = "ID do recurso relacionado", example = "1")
        Long id,
        @Schema(description = "Nome do recurso relacionado", example = "Aquário Principal")
        String nome) {
}
