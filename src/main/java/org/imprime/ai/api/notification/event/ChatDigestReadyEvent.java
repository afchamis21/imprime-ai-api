package org.imprime.ai.api.notification.event;

import java.time.Instant;
import java.util.UUID;

public record ChatDigestReadyEvent(
        UUID chatId,
        String recipientUserGuid,
        Instant windowCutoff,
        Instant digestGeneratedAt
) {
}
