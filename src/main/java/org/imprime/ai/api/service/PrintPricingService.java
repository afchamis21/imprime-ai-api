package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.print.PrintEstimateRequest;
import org.imprime.ai.api.http.response.print.PrintEstimateResponse;
import org.imprime.ai.api.http.response.print.PrintPricingConfigResponse;

public interface PrintPricingService {
    PrintPricingConfigResponse getPricingConfig();

    PrintEstimateResponse estimatePrint(PrintEstimateRequest request);
}
