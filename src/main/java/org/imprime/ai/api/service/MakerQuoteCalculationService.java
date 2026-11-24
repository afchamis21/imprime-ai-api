package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CalculateMakerQuoteRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteCalculationResponse;

public interface MakerQuoteCalculationService {
    MakerQuoteCalculationResponse calculateQuote(CalculateMakerQuoteRequest request);
}
