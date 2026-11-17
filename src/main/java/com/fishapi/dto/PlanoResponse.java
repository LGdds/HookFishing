package com.fishapi.dto;

import com.fishapi.model.Plano;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Relation(collectionRelation = "planos", itemRelation = "plano")
@Schema(name = "PlanoResponse", description = "Plano de assinatura da API")
public record PlanoResponse(
        @Schema(description = "Código do plano", example = "BASICO") Plano codigo,
        @Schema(description = "Nome comercial", example = "Básico") String nome,
        @Schema(description = "Preço mensal em reais", example = "29.90") BigDecimal precoMensal,
        @Schema(description = "Preço formatado", example = "R$ 29,90/mês") String preco,
        @Schema(description = "Requisições de uma vez (tamanho do balde)", example = "60") int capacidade,
        @Schema(description = "Fichas devolvidas ao balde por minuto", example = "120") int recargaPorMinuto,
        @Schema(description = "Ritmo de uso contínuo", example = "2 por segundo") String ritmo,
        @Schema(description = "Para quem é o plano", example = "Para aquários pequenos e aplicações com poucos usuários.") String descricao) {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public static PlanoResponse de(Plano p) {
        String preco = p.getPrecoMensal().signum() == 0
                ? "Grátis"
                : NumberFormat.getCurrencyInstance(PT_BR).format(p.getPrecoMensal()).replace(' ', ' ') + "/mês";
        return new PlanoResponse(p, p.getNome(), p.getPrecoMensal(), preco, p.getCapacidade(),
                p.getRecargaPorMinuto(), ritmo(p.getRecargaPorMinuto()), p.getDescricao());
    }

    static String ritmo(int recargaPorMinuto) {
        if (recargaPorMinuto >= 60) {
            double porSegundo = recargaPorMinuto / 60.0;
            return (porSegundo == Math.rint(porSegundo)
                    ? String.valueOf((long) porSegundo)
                    : String.format(PT_BR, "%.1f", porSegundo)) + " por segundo";
        }
        return "1 a cada " + Math.round(60.0 / recargaPorMinuto) + " s";
    }
}
