package org.imprime.ai.api.http.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Resultado da confirmação do checkout do carrinho com os pedidos criados no sistema")
public record CompleteCheckoutResponse(
        @Schema(description = "ID da sessão de checkout utilizada para idempotência", example = "cs_test_a1b2c3d4")
        String sessionId,
        @Schema(description = "Lista de pedidos criados para comprador e Makers responsáveis")
        List<OrderSummaryResponse> orders,
        @Schema(description = "Valor total consolidado dos pedidos em BRL", example = "138.70")
        BigDecimal totalAmount,
        @Schema(description = "Moeda utilizada", example = "brl")
        String currency,
        @Schema(description = "Indica se esta sessão já havia sido processada anteriormente")
        boolean alreadyProcessed
) {
}
