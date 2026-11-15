package org.imprime.ai.api.http.request.chat;

import org.imprime.ai.api.model.enums.MessageType;

public record SendMessageRequest(String content, MessageType type) {
}