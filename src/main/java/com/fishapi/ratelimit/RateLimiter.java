package com.fishapi.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting em memória com o algoritmo Token Bucket (balde de fichas).
 *
 * - Cada cliente (API Key ou IP) tem um balde com até {@code capacidade} fichas (começa cheio).
 * - Cada requisição consome 1 ficha. Sem fichas -> HTTP 429.
 * - O balde é reabastecido continuamente à taxa de {@code recargaPorSegundo} fichas por segundo,
 *   sem passar da capacidade.
 *
 * Com os valores padrão (capacidade 60, recarga de 1 ficha/s), o cliente pode fazer uma rajada de até
 * 60 requisições seguidas e depois, em média, 1 requisição por segundo (60 por minuto).
 */
@Component
public class RateLimiter {

    private static final double NANOS_POR_SEGUNDO = 1_000_000_000.0;

    private final int capacidade;
    private final double recargaPorSegundo;
    private final ConcurrentHashMap<String, Balde> baldes = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${hookfishing.rate-limit.capacidade:60}") int capacidade,
                       @Value("${hookfishing.rate-limit.recarga-por-segundo:1}") double recargaPorSegundo) {
        if (capacidade < 1 || recargaPorSegundo <= 0) {
            throw new IllegalArgumentException("Configure hookfishing.rate-limit.capacidade >= 1 e recarga-por-segundo > 0");
        }
        this.capacidade = capacidade;
        this.recargaPorSegundo = recargaPorSegundo;
    }

    public Consumo consumir(String cliente) {
        long agora = System.nanoTime();
        Consumo[] resultado = new Consumo[1];

        // compute() é atômico por cliente: duas requisições simultâneas não consomem a mesma ficha
        baldes.compute(cliente, (chave, atual) -> {
            double fichas = atual == null
                    ? capacidade
                    : Math.min(capacidade, atual.fichas() + (agora - atual.ultimaRecarga()) / NANOS_POR_SEGUNDO * recargaPorSegundo);

            boolean permitido = fichas >= 1;
            if (permitido) {
                fichas -= 1;
            }

            long ateEncher = segundos((capacidade - fichas) / recargaPorSegundo);
            long ateProximaFicha = permitido ? 0 : Math.max(1, segundos((1 - fichas) / recargaPorSegundo));
            resultado[0] = new Consumo(permitido, capacidade, (int) Math.floor(fichas), ateEncher, ateProximaFicha);
            return new Balde(fichas, agora);
        });

        if (baldes.size() > 10_000) {
            limparBaldesCheios(agora);
        }
        return resultado[0];
    }

    public int getCapacidade() {
        return capacidade;
    }

    public double getRecargaPorSegundo() {
        return recargaPorSegundo;
    }

    /** Remove baldes que já estariam cheios (clientes inativos), para não acumular memória. */
    private void limparBaldesCheios(long agora) {
        baldes.entrySet().removeIf(e ->
                e.getValue().fichas() + (agora - e.getValue().ultimaRecarga()) / NANOS_POR_SEGUNDO * recargaPorSegundo >= capacidade);
    }

    private static long segundos(double valor) {
        return (long) Math.ceil(Math.max(0, valor));
    }

    /** Estado do balde de um cliente: fichas disponíveis e momento da última recarga (System.nanoTime). */
    private record Balde(double fichas, long ultimaRecarga) {
    }

    /**
     * Resultado do consumo de uma requisição.
     *
     * @param permitido         se havia ficha disponível
     * @param limite            capacidade do balde
     * @param restantes         fichas que sobraram
     * @param resetSegundos     segundos até o balde ficar cheio de novo
     * @param retryAfterSegundos segundos até existir 1 ficha (usado no Retry-After quando bloqueado)
     */
    public record Consumo(boolean permitido, int limite, int restantes, long resetSegundos, long retryAfterSegundos) {
    }
}
