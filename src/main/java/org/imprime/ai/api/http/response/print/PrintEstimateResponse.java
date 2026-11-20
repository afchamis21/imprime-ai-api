package org.imprime.ai.api.http.response.print;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Resultado detalhado da estimativa de preço de impressão sob demanda")
public record PrintEstimateResponse(
        @Schema(description = "Custo fixo base em BRL", example = "4.00")
        BigDecimal baseFee,
        @Schema(description = "Custo calculado de material em BRL", example = "17.10")
        BigDecimal materialCost,
        @Schema(description = "Custo calculado de tempo de máquina em BRL", example = "6.25")
        BigDecimal machineTimeCost,
        @Schema(description = "Preço unitário estimado em BRL", example = "27.35")
        BigDecimal unitPrice,
        @Schema(description = "Preço total estimado considerando a quantidade em BRL", example = "27.35")
        BigDecimal totalPrice,
        @Schema(description = "Moeda ISO-4217", example = "BRL")
        String currency
) {
}
