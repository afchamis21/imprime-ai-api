package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.AdminTicketReplyRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateTicketRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminTicketDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminTicketSummaryResponse;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.springframework.data.domain.Pageable;

public interface AdminTicketService {
    PageResponse<AdminTicketSummaryResponse> listTickets(
            String q,
            SupportTicketStatus status,
            SupportTicketCategory category,
            TicketPriority priority,
            Boolean isDispute,
            String assignedAdminGuid,
            Pageable pageable
    );

    AdminTicketDetailResponse getTicketDetail(String ticketGuid);

    AdminTicketDetailResponse replyTicket(String ticketGuid, AdminTicketReplyRequest request);

    AdminTicketDetailResponse updateTicket(String ticketGuid, AdminUpdateTicketRequest request);
}
