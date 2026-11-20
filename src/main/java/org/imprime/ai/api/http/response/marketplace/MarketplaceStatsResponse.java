package org.imprime.ai.api.http.response.marketplace;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Indicadores públicos consolidados do marketplace")
public record MarketplaceStatsResponse(
        @Schema(description = "Quantidade total de Makers ativos na plataforma", example = "2400")
        long totalMakers,
        @Schema(description = "Média global de avaliações dos Makers (0.00 a 5.00)", example = "4.85")
        BigDecimal averageRating,
        @Schema(description = "Prazo mínimo típico de entrega em dias", example = "2")
        int minDeliveryDays,
        @Schema(description = "Prazo máximo típico de entrega em dias", example = "7")
        int maxDeliveryDays,
        @Schema(description = "Quantidade total de pedidos concluídos no marketplace", example = "18450")
        long totalCompletedOrders
) {
}
