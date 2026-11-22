package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Ponto da série temporal de GMV e receita de taxas no dashboard administrativo")
public record DashboardSeriesPointResponse(
        @Schema(description = "Rótulo do período (data ou mês)")
        String label,
        @Schema(description = "Volume bruto de vendas (GMV) no ponto")
        BigDecimal gmv,
        @Schema(description = "Receita de taxas da plataforma no ponto")
        BigDecimal platformFee,
        @Schema(description = "Quantidade de pedidos considerados no ponto")
        long ordersCount
) {
}
