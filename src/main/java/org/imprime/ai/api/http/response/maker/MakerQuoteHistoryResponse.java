package org.imprime.ai.api.http.response.maker;

import org.imprime.ai.api.model.enums.QuoteHistoryStatus;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record MakerQuoteHistoryResponse(
        String guid,
        String title,
        String clientName,
        QuoteHistoryStatus quoteStatus,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal finalPriceTotal,
        BigDecimal productionCostTotal,
        BigDecimal estimatedProfitTotal,
        JsonNode inputSnapshot,
        JsonNode settingsSnapshot,
        JsonNode resultSnapshot,
        Instant createdAt,
        Instant updatedAt
) {
}
