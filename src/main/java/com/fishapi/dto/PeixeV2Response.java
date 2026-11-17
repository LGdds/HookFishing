package com.fishapi.dto;

import com.fishapi.model.Alimentacao;
import com.fishapi.model.Peixe;
import com.fishapi.model.TipoAlimentacao;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

import java.util.Comparator;
import java.util.List;

/**
 * Representação do peixe na versão 2 da API (X-API-Version: 2).
 * Traz os relacionamentos detalhados e campos calculados.
 */
@Relation(collectionRelation = "peixes", itemRelation = "peixe")
@Schema(name = "PeixeResponseV2", description = "Peixe no formato da versão 2: relacionamentos detalhados e campos calculados")
public record PeixeV2Response(
        @Schema(description = "ID do peixe", example = "1") Long id,
        @Schema(description = "Nome do peixe", example = "Nemo") String nome,
        @Schema(description = "Idade em anos", example = "2") Integer idade,
        @Schema(description = "Faixa etária calculada: FILHOTE (< 1 ano), JOVEM (1 a 3 anos), ADULTO (> 3 anos)",
                example = "JOVEM", allowableValues = {"FILHOTE", "JOVEM", "ADULTO"}) String faixaEtaria,
        @Schema(description = "Tamanho em centímetros", example = "8.5") Double tamanhoCm,
        @Schema(description = "Tamanho formatado para exibição", example = "8,5 cm") String tamanhoFormatado,
        EspecieResumo especie,
        ResumoResponse aquario,
        List<AlimentacaoResumo> alimentacoes) {

    @Schema(name = "EspecieResumo", description = "Espécie do peixe")
    public record EspecieResumo(
            @Schema(example = "1") Long id,
            @Schema(example = "Peixe-palhaço") String nome,
            @Schema(example = "Pomacentridae") String familia) {
    }

    @Schema(name = "AlimentacaoResumo", description = "Alimentação do peixe")
    public record AlimentacaoResumo(
            @Schema(example = "1") Long id,
            @Schema(example = "Ração Tropical") String nome,
            @Schema(example = "RACAO") TipoAlimentacao tipo) {
    }

    public static PeixeV2Response de(Peixe p) {
        String faixa = p.getIdade() == null ? null
                : p.getIdade() < 1 ? "FILHOTE"
                : p.getIdade() <= 3 ? "JOVEM"
                : "ADULTO";
        String tamanhoFormatado = p.getTamanho() == null ? null
                : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f cm", p.getTamanho());

        return new PeixeV2Response(
                p.getId(),
                p.getNome(),
                p.getIdade(),
                faixa,
                p.getTamanho(),
                tamanhoFormatado,
                p.getEspecie() == null ? null
                        : new EspecieResumo(p.getEspecie().getId(), p.getEspecie().getNome(), p.getEspecie().getFamilia()),
                p.getAquario() == null ? null
                        : new ResumoResponse(p.getAquario().getId(), p.getAquario().getNome()),
                p.getAlimentacoes().stream()
                        .sorted(Comparator.comparing(Alimentacao::getId))
                        .map(a -> new AlimentacaoResumo(a.getId(), a.getNome(), a.getTipo()))
                        .toList());
    }
}
