package com.fishapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Chave de API usada no header X-API-Key.
 * A chave em si nunca é salva: guardamos apenas o hash SHA-256 e um prefixo para identificação.
 */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PapelAcesso papel;

    @Column(name = "hash_chave", nullable = false, unique = true, length = 64)
    private String hashChave;

    @Column(nullable = false, length = 20)
    private String prefixo;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(nullable = false)
    private Instant criadaEm;

    private Instant ultimoUsoEm;

    public ApiKey() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public PapelAcesso getPapel() {
        return papel;
    }

    public void setPapel(PapelAcesso papel) {
        this.papel = papel;
    }

    public String getHashChave() {
        return hashChave;
    }

    public void setHashChave(String hashChave) {
        this.hashChave = hashChave;
    }

    public String getPrefixo() {
        return prefixo;
    }

    public void setPrefixo(String prefixo) {
        this.prefixo = prefixo;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public void setCriadaEm(Instant criadaEm) {
        this.criadaEm = criadaEm;
    }

    public Instant getUltimoUsoEm() {
        return ultimoUsoEm;
    }

    public void setUltimoUsoEm(Instant ultimoUsoEm) {
        this.ultimoUsoEm = ultimoUsoEm;
    }
}
