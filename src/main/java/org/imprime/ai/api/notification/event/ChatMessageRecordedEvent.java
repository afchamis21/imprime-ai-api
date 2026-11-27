package org.imprime.ai.api.notification.event;

import org.imprime.ai.api.model.enums.MessageType;

import java.util.List;
import java.util.UUID;

public record ChatMessageRecordedEvent(
        UUID chatId,
        String senderGuid,
        MessageType messageType,
        List<String> recipientGuids
) {
}
