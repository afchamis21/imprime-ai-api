package org.imprime.ai.api.notification.event;

public record OrderPurchasedNotificationEvent(
        Long orderId,
        String orderGuid,
        boolean allDigital,
        boolean fromQuoteCheckout
) {
}
