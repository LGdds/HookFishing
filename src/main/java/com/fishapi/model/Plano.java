package com.fishapi.model;

import java.math.BigDecimal;

/**
 * Planos de assinatura da HookFishing API (modelo de monetização "freemium").
 *
 * Cada API Key tem um plano. O plano define o tamanho do balde do rate limit (token bucket):
 *   capacidade        -> quantas requisições dá para fazer de uma vez (rajada)
 *   recargaPorMinuto  -> quantas fichas voltam ao balde por minuto (uso contínuo)
 *
 * Quem chama a API sem chave (rotas públicas) usa o limite anônimo, por IP, definido no application.properties.
 */
public enum Plano {

    GRATUITO("Gratuito", new BigDecimal("0.00"), 20, 30,
            "Para estudar e testar a API. Uso leve, sem custo."),

    BASICO("Básico", new BigDecimal("29.90"), 60, 120,
            "Para aquários pequenos e aplicações com poucos usuários."),

    PROFISSIONAL("Profissional", new BigDecimal("99.90"), 200, 600,
            "Para redes de aquários e integrações com alto volume de requisições.");

    private final String nome;
    private final BigDecimal precoMensal;
    private final int capacidade;
    private final int recargaPorMinuto;
    private final String descricao;

    Plano(String nome, BigDecimal precoMensal, int capacidade, int recargaPorMinuto, String descricao) {
        this.nome = nome;
        this.precoMensal = precoMensal;
        this.capacidade = capacidade;
        this.recargaPorMinuto = recargaPorMinuto;
        this.descricao = descricao;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getPrecoMensal() {
        return precoMensal;
    }

    public int getCapacidade() {
        return capacidade;
    }

    public int getRecargaPorMinuto() {
        return recargaPorMinuto;
    }

    public String getDescricao() {
        return descricao;
    }
}
