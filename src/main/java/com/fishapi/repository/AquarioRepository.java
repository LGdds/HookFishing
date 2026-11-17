package com.fishapi.repository;

import com.fishapi.model.Aquario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AquarioRepository extends JpaRepository<Aquario, Long> {

    // Consulta personalizada: aquários com capacidade maior ou igual ao valor informado
    Page<Aquario> findByCapacidadeLitrosGreaterThanEqual(Integer capacidadeMinima, Pageable pageable);

    Optional<Aquario> findByCuidadorId(Long cuidadorId);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
