package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Histórico de conversa entre comprador e maker vinculado ao pedido do ticket/disputa")
public record AdminOrderChatTranscriptResponse(
        UUID chatId,
        String orderGuid,
        String clientGuid,
        String clientName,
        String companyGuid,
        String makerName,
        Instant lastMessageAt,
        List<AdminOrderChatMessageResponse> messages
) {
}
