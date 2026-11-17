package com.fishapi.dto;

import com.fishapi.seguranca.Permissoes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(name = "LoginResponse", description = "Resultado do login: a API Key a ser enviada no header X-API-Key")
public record LoginResponse(
        @Schema(description = "API Key emitida neste login. Envie-a no header X-API-Key em todas as requisições. "
                + "Cada login devolve a mesma chave; para trocá-la, use POST /api-keys/me/regenerar.",
                example = "hf_bHVjYXMgY3VpZGEgZG9zIHBlaXhlcw") String apiKey,
        @Schema(description = "Header em que a chave deve ser enviada", example = "X-API-Key") String header,
        UsuarioResponse usuario,
        @Schema(description = "O que o papel do usuário permite em cada recurso",
                example = "{\"peixes\": [\"CONSULTAR\", \"CADASTRAR\", \"EDITAR\"], \"alimentacoes\": [\"CONSULTAR\", \"CADASTRAR\", \"EDITAR\"], "
                        + "\"especies\": [\"CONSULTAR\"], \"aquarios\": [\"CONSULTAR\"], \"cuidadores\": [\"CONSULTAR\"], \"api-keys\": []}")
        Map<String, List<Permissoes.Operacao>> permissoes) {
}
