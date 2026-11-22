package org.imprime.ai.api.http.response.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Mensagem pertencente a um chamado de suporte")
public record SupportTicketMessageResponse(
        @Schema(description = "GUID da mensagem")
        String guid,
        @Schema(description = "GUID do remetente")
        String senderGuid,
        @Schema(description = "Nome do remetente", example = "Lucas Amorim")
        String senderName,
        @Schema(description = "Indica se foi enviada pela equipe de suporte", example = "false")
        boolean fromSupport,
        @Schema(description = "Conteúdo da mensagem")
        String message,
        @Schema(description = "Data e hora de envio em UTC (ISO-8601)")
        Instant createdAt,
        @Schema(description = "URLs de anexos ou fotos de evidência vinculados à mensagem")
        List<String> attachments
) {
    public SupportTicketMessageResponse(
            String guid,
            String senderGuid,
            String senderName,
            boolean fromSupport,
            String message,
            Instant createdAt
    ) {
        this(guid, senderGuid, senderName, fromSupport, message, createdAt, List.of());
    }
}
