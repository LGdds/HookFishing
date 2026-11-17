package com.fishapi.repository;

import com.fishapi.model.RegistroIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegistroIdempotenciaRepository extends JpaRepository<RegistroIdempotencia, Long> {

    Optional<RegistroIdempotencia> findByChaveAndCliente(String chave, String cliente);
}
