package org.imprime.ai.api.notification.event;

import org.imprime.ai.api.model.enums.OrderStatusCd;

public record OrderStatusChangedNotificationEvent(
        Long orderId,
        String orderGuid,
        OrderStatusCd previousStatus,
        OrderStatusCd newStatus,
        String cancellationReason,
        Long orderStatusId
) {
}
