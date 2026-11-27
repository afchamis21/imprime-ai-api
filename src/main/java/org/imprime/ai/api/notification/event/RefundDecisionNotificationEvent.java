package org.imprime.ai.api.notification.event;

import java.math.BigDecimal;

public record RefundDecisionNotificationEvent(
        String refundGuid,
        Long orderId,
        Long requesterUserId,
        Long vendorCompanyId,
        boolean approved,
        BigDecimal amount,
        String reason,
        String adminNotes,
        String refundType
) {
}
