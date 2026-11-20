package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.marketplace.MarketplaceStatsResponse;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MarketplaceService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketplaceServiceImpl implements MarketplaceService {

    @Override
    public MarketplaceStatsResponse getStats() {
        throw new NotImplementedException("MarketplaceService.getStats not yet implemented");
    }
}
