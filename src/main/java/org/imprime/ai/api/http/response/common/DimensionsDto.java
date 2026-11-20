package org.imprime.ai.api.http.response.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Dimensões físicas padronizadas em milímetros")
public record DimensionsDto(
        @Schema(description = "Largura (X) em milímetros", example = "120.0")
        BigDecimal widthMm,
        @Schema(description = "Profundidade (Y) em milímetros", example = "80.0")
        BigDecimal depthMm,
        @Schema(description = "Altura (Z) em milímetros", example = "100.0")
        BigDecimal heightMm
) {
}
