package com.fishapi.repository;

import com.fishapi.model.Cuidador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CuidadorRepository extends JpaRepository<Cuidador, Long> {

    // Consulta personalizada: busca exata por e-mail, sem diferenciar maiúsculas/minúsculas
    Optional<Cuidador> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
