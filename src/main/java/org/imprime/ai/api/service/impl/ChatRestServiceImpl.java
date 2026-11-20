package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.chat.OfferDecisionRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.chat.ChatDetailResponse;
import org.imprime.ai.api.http.response.chat.ChatSummaryResponse;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.enums.ChatRoleFilter;
import org.imprime.ai.api.model.enums.NegotiationStatus;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.ChatRestService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRestServiceImpl implements ChatRestService {

    @Override
    public PageResponse<ChatSummaryResponse> listChats(ChatRoleFilter role, String orderGuid, NegotiationStatus status, Pageable pageable) {
        throw new NotImplementedException("ChatRestService.listChats not yet implemented");
    }

    @Override
    public ChatDetailResponse getChatDetail(UUID chatId) {
        throw new NotImplementedException("ChatRestService.getChatDetail not yet implemented");
    }

    @Override
    public PageResponse<MessageDTO> listChatMessages(UUID chatId, Pageable pageable) {
        throw new NotImplementedException("ChatRestService.listChatMessages not yet implemented");
    }

    @Override
    public void markChatAsRead(UUID chatId) {
        throw new NotImplementedException("ChatRestService.markChatAsRead not yet implemented");
    }

    @Override
    public ChatDetailResponse decideOffer(UUID chatId, String messageGuid, OfferDecisionRequest request) {
        throw new NotImplementedException("ChatRestService.decideOffer not yet implemented");
    }
}
