package com.fishapi.idempotencia;

/**
 * Resultado de uma operação idempotente.
 *
 * @param resposta  corpo a ser devolvido ao cliente
 * @param repetida  true quando a chave já tinha sido usada e a resposta veio do registro anterior
 */
public record ResultadoIdempotente<R>(R resposta, boolean repetida) {
}
