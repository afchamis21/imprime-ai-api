package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Schema(description = "Distribuição de consumo e custo por material no período")
public record MaterialBreakdownDto(
        @Schema(description = "Tipo de material", example = "PLA")
        MaterialType material,
        @Schema(description = "Total consumido em gramas", example = "2160.0")
        BigDecimal grams,
        @Schema(description = "Participação percentual sobre o consumo total", example = "33.0")
        BigDecimal percentage,
        @Schema(description = "Cor representativa predominante em hexadecimal", example = "#f1f5f9")
        String colorHex,
        @Schema(description = "Custo total estimado em BRL", example = "291.60")
        BigDecimal cost
) {
}
