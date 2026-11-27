package org.imprime.ai.api.notification.event;

public record IpNoticeReceiptNotificationEvent(
        String protocol,
        String trackingToken,
        String claimantName,
        String claimantEmail,
        String targetName,
        String targetType
) {
}
