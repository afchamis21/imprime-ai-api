package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.enums.MessageType;

import java.time.Instant;

@Schema(description = "Dados de uma mensagem em um chat")
public record MessageDTO(
        @Schema(description = "Conteúdo da mensagem", example = "Olá! Gostaria de cotar esta peça em PLA preto.")
        String content,
        @Schema(description = "Tipo da mensagem (`MESSAGE` ou `OFFER`)", example = "MESSAGE")
        MessageType type,
        @Schema(description = "GUID do usuário remetente", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        String sender,
        @Schema(description = "Data e hora de envio em UTC (ISO-8601)", example = "2026-11-15T01:30:00Z")
        Instant sentAt,
        @Schema(description = "Data e hora da última modificação em UTC (ISO-8601)", example = "2026-11-15T01:30:00Z")
        Instant modifiedAt
) {
    public static MessageDTO from(ChatMessage chatMessage) {
        return new MessageDTO(chatMessage.getContent(), chatMessage.getType(), chatMessage.getSenderId(), chatMessage.getCreateDt(), chatMessage.getUpdateDt());
    }
}
