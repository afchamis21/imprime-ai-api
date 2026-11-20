package org.imprime.ai.api.http.response.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

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
        Instant createdAt
) {
}
