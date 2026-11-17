package com.fishapi.exception;

/** Limite de requisições excedido ou cliente bloqueado -> HTTP 429 Too Many Requests (com header Retry-After). */
public class LimiteRequisicoesException extends RuntimeException {

    private final long retryAfterSegundos;

    public LimiteRequisicoesException(String mensagem, long retryAfterSegundos) {
        super(mensagem);
        this.retryAfterSegundos = retryAfterSegundos;
    }

    public long getRetryAfterSegundos() {
        return retryAfterSegundos;
    }
}
