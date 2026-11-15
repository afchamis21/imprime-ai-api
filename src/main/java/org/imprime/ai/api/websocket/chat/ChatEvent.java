package org.imprime.ai.api.websocket.chat;

import lombok.Getter;
import org.imprime.ai.api.model.dto.MessageDTO;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
public final class ChatEvent<T> implements Serializable {
    private final String event;
    private final String chatId;
    private final T data;
    private final Instant timestamp;

    private ChatEvent(String event, String chatId, T data, Instant timestamp) {
        this.event = event;
        this.chatId = chatId;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static ChatEvent<MessageDTO> messageSent(
            UUID chatId,
            MessageDTO messageDTO
    ) {
        return new ChatEvent<>(
                "message.sent",
                chatId.toString(),
                messageDTO,
                Instant.now()
        );
    }
}