package com.fishapi.exception;

/** API Key válida, mas sem permissão para a operação -> HTTP 403 Forbidden. */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
