package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.OrderStatusCd;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Item de saldo retido em custódia (escrow) vinculado a pedido")
public record AdminEscrowItemResponse(
        String orderGuid,
        String orderCode,
        OrderStatusCd orderStatus,
        String buyerGuid,
        String buyerName,
        String makerGuid,
        String makerName,
        BigDecimal grossAmount,
        BigDecimal platformFeeAmount,
        BigDecimal refundedAmount,
        BigDecimal netHeldAmount,
        String holdReason,
        boolean hasOpenDispute,
        boolean hasPendingRefund,
        Instant createdAt,
        Instant updatedAt
) {
}
