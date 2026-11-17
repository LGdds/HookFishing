package com.fishapi.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting em memória com o algoritmo Token Bucket (balde de fichas), em dois níveis:
 *
 * 1. LOCAL, por cliente: cada API Key tem um balde do tamanho do seu PLANO (GRATUITO, BASICO, PROFISSIONAL);
 *    quem chama sem chave usa o balde ANONIMO do seu IP.
 *    - Cada requisição consome 1 ficha; o balde é reabastecido continuamente (recarga por minuto).
 *    - Sem fichas -> HTTP 429, com Retry-After = segundos até a próxima ficha.
 *
 * 2. PENALIDADE: quem continua insistindo com o balde vazio (N requisições negadas seguidas)
 *    é BLOQUEADO por um tempo fixo, mesmo que o balde recarregue. Se for bloqueado de novo pouco
 *    depois (dentro da janela de reincidência), o tempo de bloqueio dobra, até um máximo.
 *
 * 3. GLOBAL: um balde único para a API inteira, que a protege quando muitos clientes chegam juntos.
 *
 * Por ser em memória, os baldes zeram quando a aplicação reinicia e não são compartilhados entre
 * várias instâncias (em produção distribuída, o estado iria para um Redis, por exemplo).
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Balde> baldes = new ConcurrentHashMap<>();
    private final Balde global;

    private final Politica anonima;
    private final int penalidadeTentativas;
    private final long penalidadeMillis;
    private final long penalidadeMaximaMillis;
    private final long janelaReincidenciaMillis;

    public RateLimiter(
            @Value("${hookfishing.rate-limit.anonimo.capacidade:10}") int anonimoCapacidade,
            @Value("${hookfishing.rate-limit.anonimo.recarga-por-minuto:20}") int anonimoRecarga,
            @Value("${hookfishing.rate-limit.global.capacidade:1000}") int globalCapacidade,
            @Value("${hookfishing.rate-limit.global.recarga-por-minuto:3000}") int globalRecarga,
            @Value("${hookfishing.rate-limit.penalidade.tentativas:5}") int penalidadeTentativas,
            @Value("${hookfishing.rate-limit.penalidade.segundos:60}") long penalidadeSegundos,
            @Value("${hookfishing.rate-limit.penalidade.maximo-segundos:900}") long penalidadeMaximaSegundos,
            @Value("${hookfishing.rate-limit.penalidade.janela-reincidencia-segundos:600}") long janelaReincidenciaSegundos) {
        this.anonima = new Politica("ANONIMO", anonimoCapacidade, anonimoRecarga);
        this.global = new Balde(new Politica("GLOBAL", globalCapacidade, globalRecarga), System.currentTimeMillis());
        this.penalidadeTentativas = penalidadeTentativas;
        this.penalidadeMillis = penalidadeSegundos * 1000;
        this.penalidadeMaximaMillis = penalidadeMaximaSegundos * 1000;
        this.janelaReincidenciaMillis = janelaReincidenciaSegundos * 1000;
    }

    public Politica getPoliticaAnonima() {
        return anonima;
    }

    public int getPenalidadeTentativas() {
        return penalidadeTentativas;
    }

    public long getPenalidadeSegundos() {
        return penalidadeMillis / 1000;
    }

    public long getPenalidadeMaximaSegundos() {
        return penalidadeMaximaMillis / 1000;
    }

    public long getJanelaReincidenciaSegundos() {
        return janelaReincidenciaMillis / 1000;
    }

    /** Consome uma ficha do balde do cliente (com penalidade). */
    public Consumo consumir(String cliente, Politica politica) {
        long agora = System.currentTimeMillis();
        if (baldes.size() > 10_000) {
            baldes.entrySet().removeIf(e -> agora - e.getValue().ultimoUso > 600_000);
        }
        // O nome da política entra no id: se o plano da chave mudar, ela ganha um balde novo do tamanho certo
        Balde balde = baldes.computeIfAbsent(cliente + "|" + politica.nome(), k -> new Balde(politica, agora));
        return balde.consumir(agora, true);
    }

    /** Consome uma ficha do balde global (sem penalidade: ele não sabe quem é o culpado). */
    public Consumo consumirGlobal() {
        return global.consumir(System.currentTimeMillis(), false);
    }

    /** Regra de um balde: tamanho e velocidade de recarga. */
    public record Politica(String nome, int capacidade, int recargaPorMinuto) {

        public String descricao() {
            return nome + ";capacidade=" + capacidade + ";recarga=" + recargaPorMinuto + "/min";
        }

        /** Ex.: "1 a cada 2 s" ou "4 por segundo". */
        public String ritmo() {
            if (recargaPorMinuto >= 60) {
                double porSegundo = recargaPorMinuto / 60.0;
                return (porSegundo == Math.rint(porSegundo) ? String.valueOf((long) porSegundo) : String.format("%.1f", porSegundo))
                        + " por segundo";
            }
            return "1 a cada " + Math.round(60.0 / recargaPorMinuto) + " s";
        }
    }

    /**
     * Resultado do consumo de uma requisição.
     *
     * @param permitido              havia ficha disponível
     * @param politica               regra aplicada (plano ou ANONIMO/GLOBAL)
     * @param restantes              fichas que sobraram
     * @param resetSegundos          segundos até o balde ficar cheio de novo
     * @param retryAfterSegundos     segundos até poder tentar de novo (0 quando permitido)
     * @param bloqueado              o cliente está cumprindo um bloqueio por insistência
     * @param bloqueadoAteMillis     fim do bloqueio (epoch em ms), ou 0
     * @param tentativasAteBloqueio  quantas tentativas negadas faltam para o bloqueio (quando negado e não bloqueado)
     * @param novoBloqueio           esta requisição foi a que disparou o bloqueio
     */
    public record Consumo(boolean permitido, Politica politica, int restantes, long resetSegundos,
                          long retryAfterSegundos, boolean bloqueado, long bloqueadoAteMillis,
                          int tentativasAteBloqueio, boolean novoBloqueio) {
    }

    /** Balde de um cliente. Os métodos são synchronized: duas requisições simultâneas não usam a mesma ficha. */
    private final class Balde {

        private final Politica politica;
        private double fichas;
        private long ultimaRecarga;
        private volatile long ultimoUso;
        private int negadasSeguidas;
        private long bloqueadoAte;
        private long fimUltimoBloqueio;
        private int reincidencias;

        Balde(Politica politica, long agora) {
            this.politica = politica;
            this.fichas = politica.capacidade();
            this.ultimaRecarga = agora;
            this.ultimoUso = agora;
        }

        synchronized Consumo consumir(long agora, boolean comPenalidade) {
            ultimoUso = agora;
            recarregar(agora);

            // Cumprindo bloqueio: nega sem consumir nada
            if (comPenalidade && bloqueadoAte > agora) {
                return new Consumo(false, politica, restantes(), ateEncher(), segundos(bloqueadoAte - agora),
                        true, bloqueadoAte, 0, false);
            }

            if (fichas >= 1) {
                fichas -= 1;
                negadasSeguidas = 0;
                return new Consumo(true, politica, restantes(), ateEncher(), 0, false, 0, 0, false);
            }

            long ateProximaFicha = Math.max(1, segundos((long) Math.ceil((1 - fichas) * msPorFicha())));
            if (!comPenalidade || penalidadeTentativas <= 0) {
                return new Consumo(false, politica, 0, ateEncher(), ateProximaFicha, false, 0, 0, false);
            }

            negadasSeguidas++;
            if (negadasSeguidas >= penalidadeTentativas) {
                // Reincidência: bloqueado de novo pouco tempo depois do último bloqueio -> o tempo dobra
                boolean reincidente = fimUltimoBloqueio > 0 && agora - fimUltimoBloqueio < janelaReincidenciaMillis;
                reincidencias = reincidente ? reincidencias + 1 : 0;
                long duracao = Math.min(penalidadeMaximaMillis, penalidadeMillis << Math.min(reincidencias, 20));
                bloqueadoAte = agora + duracao;
                fimUltimoBloqueio = bloqueadoAte;
                negadasSeguidas = 0;
                return new Consumo(false, politica, 0, ateEncher(), segundos(duracao), true, bloqueadoAte, 0, true);
            }
            return new Consumo(false, politica, 0, ateEncher(), ateProximaFicha, false, 0,
                    penalidadeTentativas - negadasSeguidas, false);
        }

        private void recarregar(long agora) {
            long passou = agora - ultimaRecarga;
            if (passou > 0) {
                fichas = Math.min(politica.capacidade(), fichas + passou / msPorFicha());
                ultimaRecarga = agora;
            }
        }

        private double msPorFicha() {
            return 60_000.0 / politica.recargaPorMinuto();
        }

        private int restantes() {
            return (int) Math.floor(fichas);
        }

        private long ateEncher() {
            return segundos((long) Math.ceil((politica.capacidade() - fichas) * msPorFicha()));
        }
    }

    private static long segundos(long millis) {
        return (long) Math.ceil(Math.max(0, millis) / 1000.0);
    }
}
