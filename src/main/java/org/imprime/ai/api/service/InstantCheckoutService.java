package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.order.InstantCheckoutRequest;
import org.imprime.ai.api.http.response.order.InstantCheckoutResponse;
import org.imprime.ai.api.http.response.order.SlicingValidationStatusResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InstantCheckoutService {

    InstantCheckoutResponse createAndValidate(
            List<MultipartFile> files,
            InstantCheckoutRequest request,
            @Nullable String idempotencyKey
    );

    SlicingValidationStatusResponse getValidationStatus(String orderGuid);

    SlicingValidationStatusResponse confirmRecalculatedQuote(String orderGuid);

    SlicingValidationStatusResponse revalidateSlice(String orderGuid);

    void processQueuedValidation(String orderGuid);
}
