package org.imprime.ai.api.notification.event;

import java.math.BigDecimal;

public record PayoutNotificationEvent(
        String payoutGuid,
        String beneficiaryType,
        Long companyId,
        Long designerUserId,
        String status,
        BigDecimal amount,
        String pixKeyMasked,
        String reference,
        String failureReason
) {
}
