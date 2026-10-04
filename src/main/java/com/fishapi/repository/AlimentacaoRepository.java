package com.fishapi.repository;

import com.fishapi.model.Alimentacao;
import com.fishapi.model.TipoAlimentacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlimentacaoRepository
        extends JpaRepository<Alimentacao, Long> {

    Optional<Alimentacao> findByNomeIgnoreCase(String nome);

}