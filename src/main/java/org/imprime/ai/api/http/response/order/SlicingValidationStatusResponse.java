package org.imprime.ai.api.http.response.order;

import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SlicingValidationStatusResponse(
        String orderGuid,
        String orderCode,
        SlicingValidationStatus validationStatus,
        boolean checkoutReady,
        boolean requiresPriceConfirmation,
        BigDecimal clientQuotedAmount,
        BigDecimal serverValidatedAmount,
        BigDecimal weightDivergencePercent,
        BigDecimal timeDivergencePercent,
        String selectedPrinterName,
        PrinterSpeedTier selectedSpeedTier,
        BigDecimal selectedSpeedMultiplier,
        Integer estimatedPrintTimeSeconds,
        Integer leadTimeDays,
        boolean hasValidated3mf,
        String pollUrl,
        Instant validatedAt,
        String message,
        List<PlateValidationDetail> plates
) {
    public record PlateValidationDetail(
            int plateNumber,
            String plateName,
            String material,
            int quantity,
            BigDecimal clientModelWeightGrams,
            BigDecimal clientSupportWeightGrams,
            Integer clientBasePrintTimeSeconds,
            BigDecimal serverModelWeightGrams,
            BigDecimal serverSupportWeightGrams,
            BigDecimal serverTotalWeightGrams,
            Integer serverBasePrintTimeSeconds,
            BigDecimal weightDivergencePercent,
            BigDecimal timeDivergencePercent,
            SlicingValidationStatus status,
            boolean hasValidated3mf,
            String error
    ) {
    }
}
