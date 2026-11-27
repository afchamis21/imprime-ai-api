package org.imprime.ai.api.notification.event;

import java.time.Instant;

public record SecurityEmailNotificationEvent(
        SecurityEmailType type,
        Long userId,
        String recipientEmail,
        String recipientFirstName,
        String rawToken,
        Instant expiresAt
) {
    public enum SecurityEmailType {
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        PASSWORD_CHANGED
    }
}
