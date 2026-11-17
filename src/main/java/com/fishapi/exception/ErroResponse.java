package com.fishapi.exception;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.util.List;

/**
 * Corpo padrão de todas as respostas de erro da API.
 *
 * Estende RepresentationModel para poder ser serializado tanto como application/json
 * quanto como application/hal+json (o Swagger envia Accept: application/hal+json nos
 * endpoints HATEOAS, e o erro precisa ser compatível com esse formato).
 */
@Schema(name = "ErroResponse", description = "Formato padrão das respostas de erro da HookFishing API")
public class ErroResponse extends RepresentationModel<ErroResponse> {

    @Schema(description = "Momento em que o erro ocorreu (UTC, ISO-8601)", example = "2026-11-12T22:30:00Z")
    private final String timestamp;

    @Schema(description = "Código HTTP", example = "404")
    private final int status;

    @Schema(description = "Nome do status HTTP", example = "Not Found")
    private final String erro;

    @Schema(description = "Mensagem explicando o problema e como corrigi-lo",
            example = "Peixe com id 99 não encontrado.")
    private final String mensagem;

    @Schema(description = "Caminho da requisição que gerou o erro", example = "/peixes/99")
    private final String caminho;

    @ArraySchema(arraySchema = @Schema(description = "Campos inválidos (preenchido apenas em erros de validação)"))
    private final List<CampoInvalido> campos;

    public ErroResponse(String timestamp, int status, String erro, String mensagem,
                        String caminho, List<CampoInvalido> campos) {
        this.timestamp = timestamp;
        this.status = status;
        this.erro = erro;
        this.mensagem = mensagem;
        this.caminho = caminho;
        this.campos = campos == null ? List.of() : campos;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getErro() {
        return erro;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getCaminho() {
        return caminho;
    }

    public List<CampoInvalido> getCampos() {
        return campos;
    }

    @Schema(name = "CampoInvalido", description = "Detalhe de um campo que falhou na validação")
    public record CampoInvalido(
            @Schema(description = "Nome do campo", example = "nome")
            String campo,
            @Schema(description = "Motivo da rejeição", example = "O nome do peixe é obrigatório")
            String mensagem) {
    }
}
