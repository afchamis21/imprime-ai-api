package org.imprime.ai.api.notification.event;

public record ReviewActivityNotificationEvent(
        String reviewGuid,
        String targetType,
        Long targetId,
        Long authorUserId,
        Long reviewAuthorUserId,
        boolean isReply,
        Integer rating,
        String commentExcerpt
) {
}
