package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.support.SupportTicketMessageResponse;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.TicketPriority;

import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhamento administrativo de ticket ou disputa, incluindo evidências e chats do pedido")
public record AdminTicketDetailResponse(
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
        OrderStatusCd orderStatus,
        String assignedAdminGuid,
        String assignedAdminName,
        String resolutionNotes,
        Instant resolvedAt,
        List<SupportTicketMessageResponse> messages,
        List<String> evidenceUrls,
        List<AdminOrderChatTranscriptResponse> orderChats,
        Instant createdAt,
        Instant lastUpdatedAt
) {
}
