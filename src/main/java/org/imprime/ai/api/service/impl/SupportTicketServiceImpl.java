package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.support.AddSupportMessageRequest;
import org.imprime.ai.api.http.request.support.CreateSupportTicketRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.support.SupportTicketDetailResponse;
import org.imprime.ai.api.http.response.support.SupportTicketMessageResponse;
import org.imprime.ai.api.http.response.support.SupportTicketSummaryResponse;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.SupportTicketService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupportTicketServiceImpl implements SupportTicketService {

    @Override
    public PageResponse<SupportTicketSummaryResponse> listTickets(SupportTicketStatus status, Pageable pageable) {
        throw new NotImplementedException("SupportTicketService.listTickets not yet implemented");
    }

    @Override
    public SupportTicketDetailResponse createTicket(CreateSupportTicketRequest request) {
        throw new NotImplementedException("SupportTicketService.createTicket not yet implemented");
    }

    @Override
    public SupportTicketDetailResponse getTicketDetail(String ticketGuid) {
        throw new NotImplementedException("SupportTicketService.getTicketDetail not yet implemented");
    }

    @Override
    public SupportTicketMessageResponse addMessage(String ticketGuid, AddSupportMessageRequest request) {
        throw new NotImplementedException("SupportTicketService.addMessage not yet implemented");
    }
}
