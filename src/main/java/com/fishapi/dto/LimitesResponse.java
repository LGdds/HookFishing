package com.fishapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LimitesResponse", description = "Consumo atual do rate limit da chave e regras de penalidade")
public record LimitesResponse(
        @Schema(description = "Plano da chave usada na requisição", example = "GRATUITO") String plano,
        @Schema(description = "Requisições de uma vez (tamanho do balde)", example = "20") int capacidade,
        @Schema(description = "Fichas devolvidas por minuto", example = "30") int recargaPorMinuto,
        @Schema(description = "Ritmo de uso contínuo", example = "1 a cada 2 s") String ritmo,
        @Schema(description = "Fichas restantes depois desta consulta", example = "17") int fichasRestantes,
        @Schema(description = "Segundos até o balde ficar cheio", example = "6") long segundosAteEncher,
        @Schema(description = "Tentativas negadas seguidas que disparam o bloqueio", example = "5") int penalidadeTentativas,
        @Schema(description = "Duração do primeiro bloqueio, em segundos", example = "60") long penalidadeSegundos,
        @Schema(description = "Duração máxima do bloqueio, em segundos (o tempo dobra a cada reincidência)", example = "900") long penalidadeMaximaSegundos,
        @Schema(description = "Janela, em segundos, em que um novo bloqueio conta como reincidência", example = "600") long janelaReincidenciaSegundos,
        @Schema(description = "Resumo das regras em texto",
                example = "Plano GRATUITO: 20 requisições de uma vez, recarga de 30 por minuto (1 a cada 2 s). Ao estourar, a API responde 429 com Retry-After. 5 tentativas seguidas com o limite estourado bloqueiam a chave por 60 s; bloqueios repetidos em até 10 min dobram o tempo, até 15 min.")
        String regras) {
}
