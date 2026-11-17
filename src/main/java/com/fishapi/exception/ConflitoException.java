package com.fishapi.exception;

/** Conflito com o estado atual (duplicidade, vínculo existente) -> HTTP 409 Conflict. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
