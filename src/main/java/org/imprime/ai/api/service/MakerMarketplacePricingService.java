package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.UpdateMakerMarketplacePricingRequest;
import org.imprime.ai.api.http.response.maker.MakerMarketplacePricingResponse;

public interface MakerMarketplacePricingService {

    MakerMarketplacePricingResponse getMyPricing();

    MakerMarketplacePricingResponse updateMyPricing(UpdateMakerMarketplacePricingRequest request);
}
