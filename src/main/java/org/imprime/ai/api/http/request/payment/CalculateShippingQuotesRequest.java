package org.imprime.ai.api.http.request.payment;

import org.imprime.ai.api.model.dto.AddressDTO;

import java.util.List;

public record CalculateShippingQuotesRequest(
        List<CheckoutItemRequest> items,
        String addressGuid,
        AddressDTO shippingAddress
) { }
