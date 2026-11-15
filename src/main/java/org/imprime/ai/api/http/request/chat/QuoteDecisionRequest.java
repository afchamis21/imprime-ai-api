package org.imprime.ai.api.http.request.chat;

import org.imprime.ai.api.model.dto.MessageDTO;

public record QuoteDecisionRequest(MessageDTO message, Boolean accept) {
}
