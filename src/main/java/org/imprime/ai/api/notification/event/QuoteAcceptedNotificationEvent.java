package org.imprime.ai.api.notification.event;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteAcceptedNotificationEvent(
        UUID chatId,
        String orderGuid,
        BigDecimal offerAmount,
        String acceptedByUserGuid,
        String clientGuid,
        String companyGuid
) {
}
