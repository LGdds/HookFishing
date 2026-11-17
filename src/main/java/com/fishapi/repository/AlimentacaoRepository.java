package com.fishapi.repository;

import com.fishapi.model.Alimentacao;
import com.fishapi.model.TipoAlimentacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlimentacaoRepository extends JpaRepository<Alimentacao, Long> {

    // Consulta personalizada: filtra pelo enum TipoAlimentacao
    Page<Alimentacao> findByTipo(TipoAlimentacao tipo, Pageable pageable);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
