package com.fishapi.repository;

import com.fishapi.model.Especie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EspecieRepository extends JpaRepository<Especie, Long> {

    // Consulta personalizada: espécies de uma família (ex.: Pomacentridae)
    Page<Especie> findByFamiliaContainingIgnoreCase(String familia, Pageable pageable);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
