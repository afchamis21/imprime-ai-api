package org.imprime.ai.api.quote;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record QuoteInput(
        Integer quantity,
        String inputScope,
        Integer unitsPerPlate,
        BigDecimal printTimeHours,
        BigDecimal printTimeMinutes,
        PrinterInput printer,
        List<FilamentInput> filaments,
        Boolean requiresLabor,
        BigDecimal laborTimeMinutes,
        String laborNotes,
        BigDecimal extraCosts,
        String extraCostsDescription,
        String extraCostsScope,
        Map<String, ModuleOverrideInput> moduleOverrides
) {
    public QuoteInput(
            Integer quantity,
            String inputScope,
            BigDecimal printTimeHours,
            BigDecimal printTimeMinutes,
            PrinterInput printer,
            List<FilamentInput> filaments,
            Boolean requiresLabor,
            BigDecimal laborTimeMinutes,
            String laborNotes,
            BigDecimal extraCosts,
            String extraCostsDescription,
            Map<String, ModuleOverrideInput> moduleOverrides) {
        this(
                quantity,
                inputScope,
                null,
                printTimeHours,
                printTimeMinutes,
                printer,
                filaments,
                requiresLabor,
                laborTimeMinutes,
                laborNotes,
                extraCosts,
                extraCostsDescription,
                "per_order",
                moduleOverrides
        );
    }
    public record PrinterInput(
            String mode,
            String printerId,
            BigDecimal manualPowerWatts,
            BigDecimal manualHourlyDepreciationRate
    ) {
    }

    public record FilamentInput(
            String entryId,
            String mode,
            String filamentId,
            String manualLabel,
            BigDecimal costPerKg,
            BigDecimal weightGrams
    ) {
    }

    public record ModuleOverrideInput(
            Boolean enabled,
            Map<String, Object> config
    ) {
    }
}
