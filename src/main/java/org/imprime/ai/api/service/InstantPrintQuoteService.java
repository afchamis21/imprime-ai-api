package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.order.InstantPrintQuoteRequest;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse;

public interface InstantPrintQuoteService {

    InstantPrintQuoteResponse quoteNearbyMakers(InstantPrintQuoteRequest request);
}
