package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Resultado de uma rajada simulada contra o balde (token bucket) da API Key. */
@Schema(description = "Resultado da simulação de uma rajada de requisições")
public record SimulacaoLimiteResponse(
        @Schema(description = "Plano da chave", example = "GRATUITO") String plano,
        @Schema(description = "Requisições simuladas", example = "30") int requisicoes,
        @Schema(description = "Quantas foram atendidas", example = "19") int atendidas,
        @Schema(description = "Quantas receberiam 429 por falta de fichas", example = "4") int recusadas,
        @Schema(description = "Quantas receberiam 429 por bloqueio", example = "7") int bloqueadas,
        @Schema(description = "Se a chave terminou bloqueada", example = "true") boolean chaveBloqueada,
        @Schema(description = "Horário (UTC) em que o bloqueio termina", example = "2026-11-17T18:31:00Z") String bloqueadaAte,
        @Schema(description = "Segundos até a próxima requisição ser atendida", example = "60") long retryAfterSegundos,
        @Schema(description = "O que acontece agora com a chave",
                example = "A chave está bloqueada por 60 segundo(s). Faça qualquer requisição para ver o 429 com Retry-After e X-RateLimit-Blocked-Until.")
        String mensagem,
        @Schema(description = "Resultado de cada tentativa") List<Tentativa> tentativas) {

    @Schema(description = "Uma tentativa da rajada")
    public record Tentativa(
            @Schema(example = "21") int numero,
            @Schema(description = "ATENDIDA (200), RECUSADA (429 sem fichas) ou BLOQUEADA (429 por insistência)",
                    example = "RECUSADA") String resultado,
            @Schema(description = "Fichas restantes depois da tentativa", example = "0") long fichasRestantes,
            @Schema(description = "Retry-After que a API enviaria", example = "2") Long retryAfterSegundos) {
    }
}
