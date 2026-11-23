package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Indicadores financeiros e operacionais exibidos no dashboard do Maker")
public record MakerDashboardStatsDto(
        @Schema(description = "Saldo a liberar (pedidos em andamento) em BRL", example = "1250.00")
        BigDecimal pendingBalance,
        @Schema(description = "Saldo disponível para saque em BRL", example = "680.00")
        BigDecimal availableBalance,
        @Schema(description = "Total de vendas concluídas", example = "342")
        long totalSales,
        @Schema(description = "Impressões pendentes (pedidos com status Em Produção)", example = "4")
        int activePrints,
        @Schema(description = "Média de avaliações do estúdio", example = "4.90")
        BigDecimal averageRating
) {
}
