package org.imprime.ai.api.http.request.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Dimensões físicas em milímetros")
public record DimensionsRequest(
        @Schema(description = "Largura (eixo X) em milímetros", example = "120.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A largura é obrigatória")
        @Positive(message = "A largura deve ser positiva")
        BigDecimal widthMm,

        @Schema(description = "Profundidade (eixo Y) em milímetros", example = "80.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A profundidade é obrigatória")
        @Positive(message = "A profundidade deve ser positiva")
        BigDecimal depthMm,

        @Schema(description = "Altura (eixo Z) em milímetros", example = "100.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A altura é obrigatória")
        @Positive(message = "A altura deve ser positiva")
        BigDecimal heightMm
) {
}
