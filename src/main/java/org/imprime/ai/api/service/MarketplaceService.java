package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.marketplace.MarketplaceStatsResponse;

public interface MarketplaceService {
    MarketplaceStatsResponse getStats();
}
