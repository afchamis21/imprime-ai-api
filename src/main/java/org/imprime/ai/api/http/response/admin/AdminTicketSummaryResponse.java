package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.TicketPriority;

import java.time.Instant;

@Schema(description = "Resumo administrativo de chamado de suporte ou disputa")
public record AdminTicketSummaryResponse(
        String ticketGuid,
        String protocolCode,
        String subject,
        SupportTicketCategory category,
        SupportTicketStatus status,
        TicketPriority priority,
        boolean isDispute,
        String requesterGuid,
        String requesterName,
        String requesterEmail,
        String orderGuid,
        String orderCode,
        String assignedAdminGuid,
        String assignedAdminName,
        int messagesCount,
        int evidenceCount,
        Instant createdAt,
        Instant lastUpdatedAt
) {
}
