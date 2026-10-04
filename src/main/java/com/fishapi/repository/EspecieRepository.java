package com.fishapi.repository;

import com.fishapi.model.Especie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EspecieRepository extends JpaRepository<Especie, Long> {

    Optional<Especie> findByNomeIgnoreCase(String nome);

}