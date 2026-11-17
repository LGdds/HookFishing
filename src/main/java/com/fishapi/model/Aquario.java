package com.fishapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Aquario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome do aquário é obrigatório")
    @Size(min = 3, max = 100, message = "O nome do aquário deve ter entre 3 e 100 caracteres")
    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    @NotNull(message = "A capacidade é obrigatória")
    @Min(value = 1, message = "A capacidade deve ser de pelo menos 1 litro")
    @Column(nullable = false)
    private Integer capacidadeLitros;

    // One-to-One: cada aquário possui (no máximo) um cuidador e cada cuidador cuida de um aquário
    @OneToOne
    @JoinColumn(name = "cuidador_id", unique = true)
    private Cuidador cuidador;

    // One-to-Many: um aquário possui vários peixes
    @OneToMany(mappedBy = "aquario")
    private List<Peixe> peixes = new ArrayList<>();

    public Aquario() {
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

    public Integer getCapacidadeLitros() {
        return capacidadeLitros;
    }

    public void setCapacidadeLitros(Integer capacidadeLitros) {
        this.capacidadeLitros = capacidadeLitros;
    }

    public Cuidador getCuidador() {
        return cuidador;
    }

    public void setCuidador(Cuidador cuidador) {
        this.cuidador = cuidador;
    }

    public List<Peixe> getPeixes() {
        return peixes;
    }

    public void setPeixes(List<Peixe> peixes) {
        this.peixes = peixes;
    }
}
