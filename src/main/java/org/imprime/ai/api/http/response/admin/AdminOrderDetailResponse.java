package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.model.enums.AdminOrderType;

import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhamento administrativo completo de pedido incluindo SLA e histórico de reembolsos")
public record AdminOrderDetailResponse(
        OrderDetailResponse order,
        AdminOrderType orderType,
        String checkoutSessionId,
        String cancellationReason,
        boolean slaDelayed,
        String slaSeverity,
        long slaDelayHours,
        Instant expectedDeadlineAt,
        String slaReason,
        List<AdminRefundResponse> refunds
) {
}
