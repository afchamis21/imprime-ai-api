package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.AdminOrderType;
import org.imprime.ai.api.model.enums.OrderStatusCd;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Resumo administrativo de pedido da plataforma")
public record AdminOrderSummaryResponse(
        String orderGuid,
        String orderCode,
        OrderStatusCd status,
        AdminOrderType orderType,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal orderTotal,
        String buyerGuid,
        String buyerName,
        String buyerEmail,
        String makerGuid,
        String makerName,
        int itemsCount,
        String trackingCode,
        boolean slaDelayed,
        String slaSeverity,
        long slaDelayHours,
        String slaReason,
        Instant createdAt,
        Instant updatedAt
) {
}
