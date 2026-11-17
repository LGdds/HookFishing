package com.fishapi.dto;

import com.fishapi.model.Plano;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AssinaturaRequest", description = "Plano a contratar para a sua API Key")
public record AssinaturaRequest(
        @Schema(description = "Novo plano", example = "BASICO", allowableValues = {"GRATUITO", "BASICO", "PROFISSIONAL"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe o plano (GRATUITO, BASICO ou PROFISSIONAL)")
        Plano plano) {
}
