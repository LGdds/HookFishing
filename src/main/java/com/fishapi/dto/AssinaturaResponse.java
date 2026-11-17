package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AssinaturaResponse", description = "Resultado da troca de plano")
public record AssinaturaResponse(
        @Schema(description = "Mensagem", example = "Plano alterado para Básico. O novo limite já vale a partir da próxima requisição.") String mensagem,
        @Schema(description = "Cobrança", example = "Simulação acadêmica: nenhuma cobrança real é feita.") String cobranca,
        PlanoResponse plano) {
}
