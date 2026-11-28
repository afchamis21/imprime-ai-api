package org.imprime.ai.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageType;

import java.math.BigDecimal;
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
        Instant modifiedAt,
        @Schema(description = "Prazo de produção em dias úteis (presente em propostas)")
        Integer productionDays,
        @Schema(description = "Peso total estimado da embalagem em kg")
        BigDecimal packageWeight,
        @Schema(description = "Altura da embalagem em cm")
        BigDecimal packageHeight,
        @Schema(description = "Largura da embalagem em cm")
        BigDecimal packageWidth,
        @Schema(description = "Comprimento da embalagem em cm")
        BigDecimal packageLength
) {
    public MessageDTO(
            String messageGuid,
            String content,
            MessageType type,
            UserDTO sender,
            Instant sentAt,
            Instant modifiedAt
    ) {
        this(messageGuid, content, type, sender, sentAt, modifiedAt, null, null, null, null, null);
    }

    public static MessageDTO from(ChatMessage chatMessage, User sender) {
        return new MessageDTO(
                chatMessage.getGuid(),
                chatMessage.getContent(),
                chatMessage.getType(),
                sender != null ? UserDTO.from(sender) : null,
                chatMessage.getCreateDt(),
                chatMessage.getUpdateDt(),
                chatMessage.getProductionDays(),
                chatMessage.getPackageWeight(),
                chatMessage.getPackageHeight(),
                chatMessage.getPackageWidth(),
                chatMessage.getPackageLength()
        );
    }
}
