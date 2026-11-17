package com.fishapi.seguranca;

import com.fishapi.model.PapelAcesso;

import java.util.*;

/**
 * Matriz de permissões por papel, recurso e operação.
 *
 * | Papel     | Peixes / Alimentações        | Espécies | Aquários / Cuidadores | API Keys |
 * |-----------|------------------------------|----------|-----------------------|----------|
 * | VISITANTE | consultar                    | consultar| consultar             | -        |
 * | CUIDADOR  | consultar, cadastrar, editar | consultar| consultar             | -        |
 * | BIOLOGO   | tudo                         | tudo     | consultar             | -        |
 * | GERENTE   | tudo                         | tudo     | tudo                  | -        |
 * | ADMIN     | tudo                         | tudo     | tudo                  | tudo     |
 *
 * ("tudo" = consultar, cadastrar, editar e excluir; GET /api-keys/me é liberado para todos)
 */
public final class Permissoes {

    private Permissoes() {
    }

    public enum Recurso {
        PEIXES("peixes", "peixes"),
        ALIMENTACOES("alimentacoes", "alimentações"),
        ESPECIES("especies", "espécies"),
        AQUARIOS("aquarios", "aquários"),
        CUIDADORES("cuidadores", "cuidadores"),
        API_KEYS("api-keys", "API Keys");

        private final String caminho;
        private final String rotulo;

        Recurso(String caminho, String rotulo) {
            this.caminho = caminho;
            this.rotulo = rotulo;
        }

        public String getCaminho() {
            return caminho;
        }

        public String getRotulo() {
            return rotulo;
        }

        /** Descobre o recurso pelo primeiro segmento da URL (ex.: "/peixes/1" -> PEIXES). */
        public static Optional<Recurso> doCaminho(String caminho) {
            String semBarra = caminho.startsWith("/") ? caminho.substring(1) : caminho;
            int fim = semBarra.indexOf('/');
            String segmento = fim < 0 ? semBarra : semBarra.substring(0, fim);
            return Arrays.stream(values()).filter(r -> r.caminho.equals(segmento)).findFirst();
        }
    }

    public enum Operacao {
        CONSULTAR("consultar"),
        CADASTRAR("cadastrar"),
        EDITAR("editar"),
        EXCLUIR("excluir");

        private final String verbo;

        Operacao(String verbo) {
            this.verbo = verbo;
        }

        public String getVerbo() {
            return verbo;
        }

        public static Operacao doMetodo(String metodoHttp) {
            return switch (metodoHttp.toUpperCase(Locale.ROOT)) {
                case "GET", "HEAD" -> CONSULTAR;
                case "POST" -> CADASTRAR;
                case "DELETE" -> EXCLUIR;
                default -> EDITAR;
            };
        }
    }

    public static boolean permite(PapelAcesso papel, Recurso recurso, Operacao operacao) {
        if (recurso == Recurso.API_KEYS) {
            return papel == PapelAcesso.ADMIN;
        }
        if (operacao == Operacao.CONSULTAR) {
            return true;
        }
        return switch (papel) {
            case VISITANTE -> false;
            case CUIDADOR -> (recurso == Recurso.PEIXES || recurso == Recurso.ALIMENTACOES)
                    && (operacao == Operacao.CADASTRAR || operacao == Operacao.EDITAR);
            case BIOLOGO -> recurso == Recurso.PEIXES || recurso == Recurso.ALIMENTACOES || recurso == Recurso.ESPECIES;
            case GERENTE, ADMIN -> true;
        };
    }

    public static List<PapelAcesso> papeisPermitidos(Recurso recurso, Operacao operacao) {
        return Arrays.stream(PapelAcesso.values())
                .filter(p -> permite(p, recurso, operacao))
                .toList();
    }

    /** Permissões de um papel em formato amigável para JSON: { "peixes": ["CONSULTAR", ...], ... }. */
    public static Map<String, List<Operacao>> mapaDe(PapelAcesso papel) {
        Map<String, List<Operacao>> mapa = new LinkedHashMap<>();
        for (Recurso recurso : Recurso.values()) {
            List<Operacao> operacoes = Arrays.stream(Operacao.values())
                    .filter(op -> permite(papel, recurso, op))
                    .toList();
            mapa.put(recurso.getCaminho(), operacoes);
        }
        return mapa;
    }

    /** Ex.: "BIOLOGO, GERENTE ou ADMIN". */
    public static String listar(List<PapelAcesso> papeis) {
        List<String> nomes = papeis.stream().map(Enum::name).toList();
        if (nomes.size() <= 1) {
            return String.join("", nomes);
        }
        return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " ou " + nomes.get(nomes.size() - 1);
    }
}
