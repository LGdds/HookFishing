package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Política de CORS da API e o resultado da verificação de uma origem. */
@Schema(description = "Política de CORS e verificação de uma origem")
public record CorsResponse(
        @Schema(description = "Origem verificada", example = "http://localhost:5500") String origem,
        @Schema(description = "Se o navegador seria autorizado a chamar a API a partir dessa origem", example = "true")
        Boolean permitida,
        @Schema(description = "Explicação do resultado",
                example = "A origem http://localhost:5500 está na lista de origens permitidas: o navegador recebe Access-Control-Allow-Origin e libera a resposta.")
        String motivo,
        @Schema(description = "Origens autorizadas") List<String> origensPermitidas,
        @Schema(description = "Métodos HTTP autorizados") List<String> metodosPermitidos,
        @Schema(description = "Headers que o navegador pode enviar") List<String> headersPermitidos,
        @Schema(description = "Headers de resposta que o JavaScript pode ler") List<String> headersExpostos,
        @Schema(description = "Por quanto tempo o navegador guarda o resultado do preflight (segundos)", example = "3600")
        Long cachePreflightSegundos) {
}
