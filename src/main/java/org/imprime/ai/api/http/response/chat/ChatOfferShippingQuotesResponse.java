package org.imprime.ai.api.http.response.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.dto.AddressDTO;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Opções de frete calculadas via Melhor Envio para uma proposta de orçamento no chat")
public record ChatOfferShippingQuotesResponse(
        String messageGuid,
        String orderGuid,
        BigDecimal serviceAmount,
        Integer productionDays,
        BigDecimal packageWeight,
        BigDecimal packageHeight,
        BigDecimal packageWidth,
        BigDecimal packageLength,
        boolean addressRequired,
        AddressDTO destinationAddress,
        String originZipCode,
        List<ChatOfferShippingOption> options
) {
    public record ChatOfferShippingOption(
            String quoteGuid,
            Integer serviceId,
            String serviceName,
            String carrierName,
            BigDecimal price,
            Integer carrierDeliveryDays,
            Integer totalDeliveryDays,
            BigDecimal totalAmount
    ) {
    }
}
