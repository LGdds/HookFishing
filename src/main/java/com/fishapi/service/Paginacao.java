package com.fishapi.service;

import com.fishapi.exception.ParametroInvalidoException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/** Valida os parâmetros de paginação/ordenação antes de consultar o banco. */
public final class Paginacao {

    private Paginacao() {
    }

    public static void validarOrdenacao(Pageable pageable, Set<String> camposPermitidos) {
        for (Sort.Order ordem : pageable.getSort()) {
            if (!camposPermitidos.contains(ordem.getProperty())) {
                throw new ParametroInvalidoException(
                        "Campo de ordenação inválido: '" + ordem.getProperty() + "'. Campos permitidos: "
                                + String.join(", ", new TreeSet<>(camposPermitidos))
                                + ". Exemplo: sort=nome,asc");
            }
        }
    }

    public static String textoObrigatorio(String valor, String nomeParametro) {
        if (valor == null || valor.isBlank()) {
            throw new ParametroInvalidoException("O parâmetro '" + nomeParametro + "' não pode ser vazio.");
        }
        return valor.trim();
    }
}
