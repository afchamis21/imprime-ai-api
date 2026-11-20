package org.imprime.ai.api.http.response.print;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "Constantes públicas de precificação para o fatiador e calculadora de impressão")
public record PrintPricingConfigResponse(
        @Schema(description = "Taxa base por pedido em BRL", example = "4.00")
        BigDecimal basePrice,
        @Schema(description = "Valor por grama de material em BRL", example = "0.20")
        BigDecimal pricePerGram,
        @Schema(description = "Valor por hora de máquina em BRL", example = "2.50")
        BigDecimal pricePerHour,
        @Schema(description = "Moeda padrão ISO-4217", example = "BRL")
        String currency,
        @Schema(description = "Multiplicadores aplicados por tipo de material")
        Map<MaterialType, BigDecimal> materialMultipliers
) {
}
