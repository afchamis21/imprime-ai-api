package org.imprime.ai.api.notification.event;

public record ModerationNotificationEvent(
        String targetType,
        String targetGuid,
        Long targetUserId,
        Long companyId,
        String targetName,
        String action,
        String reason,
        String dmcaClaimant,
        String dmcaReference
) {
}
