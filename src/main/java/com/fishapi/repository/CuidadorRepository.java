package com.fishapi.repository;

import com.fishapi.model.Cuidador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CuidadorRepository extends JpaRepository<Cuidador, Long> {

    Optional<Cuidador> findByEmailIgnoreCase(String email);

}