package org.imprime.ai.api.model.dto;

import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.enums.MessageType;

import java.time.Instant;

public record MessageDTO(String content, MessageType type, String sender, Instant sentAt, Instant modifiedAt) {
    public static MessageDTO from(ChatMessage chatMessage) {
        return new MessageDTO(chatMessage.getContent(), chatMessage.getType(), chatMessage.getSenderId(), chatMessage.getCreateDt(), chatMessage.getUpdateDt());
    }
}
