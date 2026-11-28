package org.imprime.ai.api.http.request.payment;

import jakarta.validation.constraints.Size;

public record CreateQuoteCheckoutRequest(
        @Size(max = 64) String shippingQuoteGuid,
        @Size(max = 64) String addressGuid
) {
}
