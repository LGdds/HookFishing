package com.fishapi.repository;

import com.fishapi.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByHashChave(String hashChave);

    boolean existsByHashChave(String hashChave);
}
