package com.fishapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Peixe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome do peixe é obrigatório")
    @Size(min = 2, max = 100)
    private String nome;

    @NotNull(message = "A idade é obrigatória")
    @Min(value = 0, message = "A idade não pode ser negativa")
    private Integer idade;

    @NotNull(message = "O tamanho é obrigatório")
    @Positive(message = "O tamanho deve ser maior que zero")
    private Double tamanho;

    @ManyToOne
    @JoinColumn(name = "especie_id")
    private Especie especie;

    @ManyToOne
    @JoinColumn(name = "aquario_id")
    private Aquario aquario;

    @ManyToMany
    @JoinTable(
            name = "peixe_alimentacao",
            joinColumns = @JoinColumn(name = "peixe_id"),
            inverseJoinColumns = @JoinColumn(name = "alimentacao_id")
    )
    private List<Alimentacao> alimentacoes = new ArrayList<>();

    public Peixe() {
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

    public Integer getIdade() {
        return idade;
    }

    public void setIdade(Integer idade) {
        this.idade = idade;
    }

    public Double getTamanho() {
        return tamanho;
    }

    public void setTamanho(Double tamanho) {
        this.tamanho = tamanho;
    }

    public Especie getEspecie() {
        return especie;
    }

    public void setEspecie(Especie especie) {
        this.especie = especie;
    }

    public Aquario getAquario() {
        return aquario;
    }

    public void setAquario(Aquario aquario) {
        this.aquario = aquario;
    }

    public List<Alimentacao> getAlimentacoes() {
        return alimentacoes;
    }

    public void setAlimentacoes(List<Alimentacao> alimentacoes) {
        this.alimentacoes = alimentacoes;
    }
}