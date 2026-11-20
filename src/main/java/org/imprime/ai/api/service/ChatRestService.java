package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.chat.OfferDecisionRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.chat.ChatDetailResponse;
import org.imprime.ai.api.http.response.chat.ChatSummaryResponse;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.enums.ChatRoleFilter;
import org.imprime.ai.api.model.enums.NegotiationStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ChatRestService {
    PageResponse<ChatSummaryResponse> listChats(ChatRoleFilter role, String orderGuid, NegotiationStatus status, Pageable pageable);

    ChatDetailResponse getChatDetail(UUID chatId);

    PageResponse<MessageDTO> listChatMessages(UUID chatId, Pageable pageable);

    void markChatAsRead(UUID chatId);

    ChatDetailResponse decideOffer(UUID chatId, String messageGuid, OfferDecisionRequest request);
}
