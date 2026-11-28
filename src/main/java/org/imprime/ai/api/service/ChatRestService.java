package org.imprime.ai.api.service;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.http.request.chat.CalculateChatOfferShippingRequest;
import org.imprime.ai.api.http.request.chat.CreateChatOfferRequest;
import org.imprime.ai.api.http.request.chat.OfferDecisionRequest;
import org.imprime.ai.api.http.request.chat.UpdateChatSellerNotesRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.chat.ChatDetailResponse;
import org.imprime.ai.api.http.response.chat.ChatOfferShippingQuotesResponse;
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

    MessageDTO createOffer(UUID chatId, CreateChatOfferRequest request);

    ChatOfferShippingQuotesResponse calculateOfferShippingQuotes(
            UUID chatId,
            String messageGuid,
            @Nullable CalculateChatOfferShippingRequest request
    );

    ChatDetailResponse decideOffer(UUID chatId, String messageGuid, OfferDecisionRequest request);

    ChatDetailResponse updateSellerNotes(UUID chatId, UpdateChatSellerNotesRequest request);
}
