package com.fishapi.repository;

import com.fishapi.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByHashChave(String hashChave);

    boolean existsByHashChave(String hashChave);

    /**
     * Atualiza SÓ a data de último uso. Salvar a entidade inteira em cada requisição podia sobrescrever o hash
     * com um valor antigo quando duas requisições rodavam ao mesmo tempo que uma troca de chave.
     */
    @Modifying
    @Query("update ApiKey a set a.ultimoUsoEm = :agora where a.id = :id")
    int registrarUso(@Param("id") Long id, @Param("agora") Instant agora);
}
