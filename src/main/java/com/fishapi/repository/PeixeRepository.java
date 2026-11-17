package com.fishapi.repository;

import com.fishapi.model.Peixe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeixeRepository extends JpaRepository<Peixe, Long> {

    // Consulta personalizada: busca paginada por parte do nome, sem diferenciar maiúsculas/minúsculas
    Page<Peixe> findByNomeContainingIgnoreCase(String nome, Pageable pageable);

    boolean existsByEspecieId(Long especieId);

    long countByEspecieId(Long especieId);

    long countByAquarioId(Long aquarioId);
}
