package org.imprime.ai.api.http.response.support;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;

import java.time.Instant;

@Schema(description = "Resumo de um chamado de suporte")
public record SupportTicketSummaryResponse(
        @Schema(description = "GUID único do chamado")
        String ticketGuid,
        @Schema(description = "Código de protocolo legível", example = "SUP-0042")
        String protocolCode,
        @Schema(description = "Assunto do chamado", example = "Dúvida sobre rastreio do Pedido PED-1029")
        String subject,
        @Schema(description = "Categoria do chamado", example = "ORDER")
        SupportTicketCategory category,
        @Schema(description = "Status atual do atendimento", example = "IN_REVIEW")
        SupportTicketStatus status,
        @Schema(description = "GUID do pedido vinculado (opcional)")
        String orderGuid,
        @Schema(description = "Quantidade de mensagens trocadas", example = "2")
        int messagesCount,
        @Schema(description = "Data de abertura em UTC (ISO-8601)")
        Instant createdAt,
        @Schema(description = "Data da última atualização em UTC (ISO-8601)")
        Instant lastUpdatedAt
) {
}
