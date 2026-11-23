package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateMakerQuoteRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteHistoryResponse;
import org.imprime.ai.api.model.enums.QuoteHistoryStatus;
import org.springframework.data.domain.Pageable;

public interface MakerQuoteHistoryService {
    PageResponse<MakerQuoteHistoryResponse> listQuotes(QuoteHistoryStatus status, String query, Pageable pageable);

    MakerQuoteHistoryResponse createQuote(CreateMakerQuoteRequest request);

    MakerQuoteHistoryResponse updateQuoteStatus(String quoteGuid, UpdateMakerQuoteStatusRequest request);

    void deleteQuote(String quoteGuid);
}
