package com.fishapi.exception;

/** Parâmetro, header ou filtro inválido -> HTTP 400 Bad Request. */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
