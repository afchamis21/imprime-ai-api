package org.imprime.ai.api.http.response.payment;

import java.math.BigDecimal;
import java.util.List;

public record ShippingQuotesResponse(List<VendorQuotes> vendors) {
    public record VendorQuotes(
            String vendorGuid,
            String vendorName,
            List<ShippingOption> options
    ) { }

    public record ShippingOption(
            String quoteGuid,
            Integer serviceId,
            String serviceName,
            String carrierName,
            BigDecimal price,
            Integer deliveryDays
    ) { }
}
