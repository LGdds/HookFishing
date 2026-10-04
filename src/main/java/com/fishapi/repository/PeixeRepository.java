package com.fishapi.repository;

import com.fishapi.model.Peixe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PeixeRepository extends JpaRepository<Peixe, Long> {

    Optional<Peixe> findByNomeIgnoreCase(String nome);

}