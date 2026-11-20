package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.support.AddSupportMessageRequest;
import org.imprime.ai.api.http.request.support.CreateSupportTicketRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.support.SupportTicketDetailResponse;
import org.imprime.ai.api.http.response.support.SupportTicketMessageResponse;
import org.imprime.ai.api.http.response.support.SupportTicketSummaryResponse;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.springframework.data.domain.Pageable;

public interface SupportTicketService {
    PageResponse<SupportTicketSummaryResponse> listTickets(SupportTicketStatus status, Pageable pageable);

    SupportTicketDetailResponse createTicket(CreateSupportTicketRequest request);

    SupportTicketDetailResponse getTicketDetail(String ticketGuid);

    SupportTicketMessageResponse addMessage(String ticketGuid, AddSupportMessageRequest request);
}
