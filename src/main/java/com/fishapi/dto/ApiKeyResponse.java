package com.fishapi.dto;

import com.fishapi.model.ApiKey;
import com.fishapi.model.PapelAcesso;
import com.fishapi.seguranca.Permissoes;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

import java.util.List;
import java.util.Map;

/**
 * Dados de uma API Key. O campo 'chave' (valor completo) só é preenchido no momento
 * da criação ou regeneração; depois disso apenas o 'prefixo' é exibido.
 */
@Relation(collectionRelation = "apiKeys", itemRelation = "apiKey")
@Schema(name = "ApiKeyResponse", description = "API Key cadastrada")
public record ApiKeyResponse(
        @Schema(description = "ID da chave", example = "2") Long id,
        @Schema(description = "Dono da chave", example = "Equipe de Cuidadores") String nome,
        @Schema(description = "E-mail do responsável", example = "cuidador@hookfishing.com") String email,
        @Schema(description = "Papel de acesso", example = "CUIDADOR") PapelAcesso papel,
        @Schema(description = "O que o papel permite fazer em cada recurso",
                example = "{\"peixes\": [\"CONSULTAR\", \"CADASTRAR\", \"EDITAR\"], \"alimentacoes\": [\"CONSULTAR\", \"CADASTRAR\", \"EDITAR\"], "
                        + "\"especies\": [\"CONSULTAR\"], \"aquarios\": [\"CONSULTAR\"], \"cuidadores\": [\"CONSULTAR\"], \"api-keys\": []}")
        Map<String, List<Permissoes.Operacao>> permissoes,
        @Schema(description = "Primeiros caracteres da chave, para identificação", example = "hf_cuidador") String prefixo,
        @Schema(description = "Indica se a chave está ativa", example = "true") boolean ativa,
        @Schema(description = "Data de criação (UTC)", example = "2026-11-12T22:00:00Z") String criadaEm,
        @Schema(description = "Último uso (UTC)", example = "2026-11-12T22:15:42Z") String ultimoUsoEm,
        @Schema(description = "Valor completo da chave. Exibido SOMENTE na criação/regeneração: guarde-o, ele não poderá ser consultado depois.",
                example = "hf_Q2xhdWRlIGVzdGV2ZSBhcXVpIDop") String chave,
        @Schema(description = "Observação sobre a chave",
                example = "Guarde esta chave em local seguro: ela não será exibida novamente.") String aviso) {

    /** Cópia desta resposta com outra observação (a chave completa nunca é repetida). */
    public ApiKeyResponse comAviso(String novoAviso) {
        return new ApiKeyResponse(id, nome, email, papel, permissoes, prefixo, ativa, criadaEm, ultimoUsoEm, null, novoAviso);
    }

    public static ApiKeyResponse de(ApiKey k) {
        return comChave(k, null, null);
    }

    public static ApiKeyResponse comChave(ApiKey k, String chaveCompleta, String aviso) {
        return new ApiKeyResponse(
                k.getId(),
                k.getNome(),
                k.getEmail(),
                k.getPapel(),
                Permissoes.mapaDe(k.getPapel()),
                k.getPrefixo(),
                k.isAtiva(),
                k.getCriadaEm() == null ? null : k.getCriadaEm().toString(),
                k.getUltimoUsoEm() == null ? null : k.getUltimoUsoEm().toString(),
                chaveCompleta,
                aviso);
    }
}
