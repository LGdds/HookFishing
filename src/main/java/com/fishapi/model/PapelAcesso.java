package com.fishapi.model;

/**
 * Papéis de acesso de uma API Key, inspirados na equipe de um aquário.
 * A matriz de permissões de cada papel fica em {@link com.fishapi.seguranca.Permissoes}.
 */
public enum PapelAcesso {

    /** Público do aquário: apenas consulta. */
    VISITANTE("Visitante", "Apenas consulta peixes, espécies, aquários, cuidadores e alimentações."),

    /** Equipe do dia a dia: cadastra peixes, ajusta a dieta e move peixes entre aquários. */
    CUIDADOR("Cuidador", "Consulta tudo; cadastra e edita peixes e alimentações. Não exclui nada."),

    /** Parte científica: espécies, peixes e alimentações. */
    BIOLOGO("Biólogo", "Consulta tudo; gerencia por completo peixes, alimentações e espécies."),

    /** Estrutura do aquário: aquários e equipe de cuidadores. */
    GERENTE("Gerente", "Gerencia por completo peixes, alimentações, espécies, aquários e cuidadores."),

    /** Acesso total, inclusive ao gerenciamento de API Keys. */
    ADMIN("Administrador", "Acesso total, inclusive gerar e gerenciar API Keys.");

    private final String rotulo;
    private final String descricao;

    PapelAcesso(String rotulo, String descricao) {
        this.rotulo = rotulo;
        this.descricao = descricao;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getDescricao() {
        return descricao;
    }
}
