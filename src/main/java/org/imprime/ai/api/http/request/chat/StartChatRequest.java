package org.imprime.ai.api.http.request.chat;

public record StartChatRequest(
        SendMessageRequest message,
        String makerGuid,
        String orderGuid
) {
}
