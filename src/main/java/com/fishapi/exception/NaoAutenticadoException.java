package com.fishapi.exception;

/** API Key ausente, inválida ou revogada -> HTTP 401 Unauthorized. */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
