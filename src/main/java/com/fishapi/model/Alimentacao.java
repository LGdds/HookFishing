package com.fishapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Alimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome da alimentação é obrigatório")
    @Size(min = 3, max = 100, message = "O nome da alimentação deve ter entre 3 e 100 caracteres")
    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    // Enum TipoAlimentacao
    @NotNull(message = "O tipo de alimentação é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAlimentacao tipo;

    // Lado inverso do Many-to-Many com Peixe
    @ManyToMany(mappedBy = "alimentacoes")
    private List<Peixe> peixes = new ArrayList<>();

    public Alimentacao() {
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

    public TipoAlimentacao getTipo() {
        return tipo;
    }

    public void setTipo(TipoAlimentacao tipo) {
        this.tipo = tipo;
    }

    public List<Peixe> getPeixes() {
        return peixes;
    }

    public void setPeixes(List<Peixe> peixes) {
        this.peixes = peixes;
    }
}
