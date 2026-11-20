package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.CostCategory;

import java.math.BigDecimal;

@Schema(description = "Composição de custos operacionais por categoria")
public record CostBreakdownDto(
        @Schema(description = "Categoria semântica de custo", example = "FILAMENTS_AND_RESINS")
        CostCategory category,
        @Schema(description = "Valor acumulado em BRL", example = "512.00")
        BigDecimal amount,
        @Schema(description = "Participação percentual sobre os custos totais", example = "49.0")
        BigDecimal percentage
) {
}
