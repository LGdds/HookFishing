package com.fishapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Guarda as chaves X-Idempotency-Key já utilizadas em requisições POST,
 * para que a mesma operação não seja processada duas vezes.
 */
@Entity
@Table(
        name = "registro_idempotencia",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chave", "cliente"})
)
public class RegistroIdempotencia {

    public enum Status {
        EM_PROCESSAMENTO,
        CONCLUIDA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String chave;

    /** Identifica o dono da chave (id da API Key), evitando colisão entre clientes diferentes. */
    @Column(nullable = false, length = 50)
    private String cliente;

    @Column(nullable = false, length = 100)
    private String operacao;

    @Column(name = "hash_requisicao", nullable = false, length = 64)
    private String hashRequisicao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private Long recursoId;

    @Column(nullable = false)
    private Instant criadoEm;

    @Column(nullable = false)
    private Instant expiraEm;

    public RegistroIdempotencia() {
    }

    public Long getId() {
        return id;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public String getOperacao() {
        return operacao;
    }

    public void setOperacao(String operacao) {
        this.operacao = operacao;
    }

    public String getHashRequisicao() {
        return hashRequisicao;
    }

    public void setHashRequisicao(String hashRequisicao) {
        this.hashRequisicao = hashRequisicao;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getRecursoId() {
        return recursoId;
    }

    public void setRecursoId(Long recursoId) {
        this.recursoId = recursoId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(Instant expiraEm) {
        this.expiraEm = expiraEm;
    }
}
