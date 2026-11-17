package com.fishapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Usuário do front-end (login com e-mail e senha).
 * Cada usuário tem a sua própria API Key: o papel da chave define o que ele pode fazer,
 * e a cada login a API emite um novo valor para essa chave.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /** Hash PBKDF2 da senha (a senha em si nunca é salva). */
    @Column(name = "senha_hash", nullable = false, length = 200)
    private String senhaHash;

    @OneToOne(optional = false)
    @JoinColumn(name = "api_key_id", nullable = false, unique = true)
    private ApiKey apiKey;

    @Column(nullable = false)
    private Instant criadoEm;

    public Usuario() {
    }

    public Long getId() {
        return id;
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

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }
}
