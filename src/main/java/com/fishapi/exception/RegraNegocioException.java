package com.fishapi.exception;

/** Requisição bem formada, mas que viola uma regra de negócio -> HTTP 422 Unprocessable Content. */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
