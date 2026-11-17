package com.fishapi.seguranca;

import com.fishapi.model.PapelAcesso;
import com.fishapi.model.Plano;

/** Dados da API Key que fez a requisição atual. */
public record ClienteAutenticado(Long apiKeyId, String nome, PapelAcesso papel, Plano plano) {
}
