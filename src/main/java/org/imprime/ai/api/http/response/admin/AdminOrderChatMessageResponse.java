package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MessageType;

import java.time.Instant;

@Schema(description = "Mensagem registrada no chat de pedido entre comprador e maker")
public record AdminOrderChatMessageResponse(
        String guid,
        String senderGuid,
        String senderName,
        String senderRole,
        String content,
        MessageType type,
        Instant createdAt
) {
}
