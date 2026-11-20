package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.print.PrintEstimateRequest;
import org.imprime.ai.api.http.response.print.PrintEstimateResponse;
import org.imprime.ai.api.http.response.print.PrintPricingConfigResponse;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.PrintPricingService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrintPricingServiceImpl implements PrintPricingService {

    @Override
    public PrintPricingConfigResponse getPricingConfig() {
        throw new NotImplementedException("PrintPricingService.getPricingConfig not yet implemented");
    }

    @Override
    public PrintEstimateResponse estimatePrint(PrintEstimateRequest request) {
        throw new NotImplementedException("PrintPricingService.estimatePrint not yet implemented");
    }
}
