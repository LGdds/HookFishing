package com.fishapi.exception;

/** Recurso inexistente -> HTTP 404 Not Found. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
