package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageType;

import java.time.Instant;

@Schema(description = "Dados de uma mensagem em um chat")
public record MessageDTO(
        @Schema(description = "GUID único da mensagem", example = "8a2f7f10-3c90-4a12-b89e-111122223333")
        String messageGuid,
        @Schema(description = "Conteúdo da mensagem", example = "Olá! Gostaria de cotar esta peça em PLA preto.")
        String content,
        @Schema(description = "Tipo da mensagem (`MESSAGE`, `OFFER` ou `SYSTEM`)", example = "MESSAGE")
        MessageType type,
        @Schema(description = "Usuário remetente")
        UserDTO sender,
        @Schema(description = "Data e hora de envio em UTC (ISO-8601)", example = "2026-11-15T01:30:00Z")
        Instant sentAt,
        @Schema(description = "Data e hora da última modificação em UTC (ISO-8601)", example = "2026-11-15T01:30:00Z")
        Instant modifiedAt
) {
    public static MessageDTO from(ChatMessage chatMessage, User sender) {
        return new MessageDTO(
                chatMessage.getGuid(),
                chatMessage.getContent(),
                chatMessage.getType(),
                UserDTO.from(sender),
                chatMessage.getCreateDt(),
                chatMessage.getUpdateDt()
        );
    }
}
