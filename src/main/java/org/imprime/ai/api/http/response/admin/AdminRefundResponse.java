package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.RefundType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Dados administrativos de uma solicitação/operação de reembolso")
public record AdminRefundResponse(
        String guid,
        String orderGuid,
        String orderCode,
        BigDecimal orderTotal,
        String requestedByGuid,
        String requestedByName,
        String requestedByEmail,
        RefundType refundType,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        RefundStatus refundStatus,
        String reason,
        List<String> evidenceUrls,
        String adminNotes,
        String rejectionReason,
        String stripeRefundId,
        String reviewedByName,
        Instant reviewedAt,
        Instant createdAt
) {
}
