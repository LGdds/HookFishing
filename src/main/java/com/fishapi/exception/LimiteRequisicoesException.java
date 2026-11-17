package com.fishapi.exception;

/** Limite de requisições excedido -> HTTP 429 Too Many Requests (com header Retry-After). */
public class LimiteRequisicoesException extends RuntimeException {

    private final long retryAfterSegundos;
    private final int limite;

    public LimiteRequisicoesException(long retryAfterSegundos, int limite, double recargaPorSegundo) {
        super("Limite de requisições excedido: o balde de " + limite + " requisições está vazio e é reabastecido com "
                + formatar(recargaPorSegundo) + " requisição(ões) por segundo. Tente novamente em "
                + retryAfterSegundos + " segundo(s).");
        this.retryAfterSegundos = retryAfterSegundos;
        this.limite = limite;
    }

    private static String formatar(double valor) {
        return valor == Math.rint(valor) ? String.valueOf((long) valor) : String.valueOf(valor);
    }

    public long getRetryAfterSegundos() {
        return retryAfterSegundos;
    }

    public int getLimite() {
        return limite;
    }
}
