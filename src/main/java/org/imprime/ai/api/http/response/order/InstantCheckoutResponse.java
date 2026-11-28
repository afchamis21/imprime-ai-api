package org.imprime.ai.api.http.response.order;

import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;

import java.math.BigDecimal;
import java.util.List;

public record InstantCheckoutResponse(
        String orderGuid,
        String orderCode,
        String makerGuid,
        String makerName,
        SlicingValidationStatus validationStatus,
        boolean checkoutReady,
        boolean requiresPriceConfirmation,
        BigDecimal clientQuotedAmount,
        BigDecimal serverValidatedAmount,
        String selectedPrinterName,
        PrinterSpeedTier selectedSpeedTier,
        BigDecimal selectedSpeedMultiplier,
        Integer estimatedPrintTimeSeconds,
        Integer leadTimeDays,
        String pollUrl,
        String message,
        List<SlicingValidationStatusResponse.PlateValidationDetail> plates
) {
    public static InstantCheckoutResponse fromStatus(
            SlicingValidationStatusResponse status,
            String makerGuid,
            String makerName
    ) {
        return new InstantCheckoutResponse(
                status.orderGuid(),
                status.orderCode(),
                makerGuid,
                makerName,
                status.validationStatus(),
                status.checkoutReady(),
                status.requiresPriceConfirmation(),
                status.clientQuotedAmount(),
                status.serverValidatedAmount(),
                status.selectedPrinterName(),
                status.selectedSpeedTier(),
                status.selectedSpeedMultiplier(),
                status.estimatedPrintTimeSeconds(),
                status.leadTimeDays(),
                status.pollUrl(),
                status.message(),
                status.plates()
        );
    }
}
