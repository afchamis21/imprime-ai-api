package org.imprime.ai.api.quote;

import lombok.Getter;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;

import java.math.BigDecimal;
import java.util.Map;

@Getter
public class QuotingContext {
    private final QuoteInput input;
    private final Company company;
    private final int quantity;
    private final String inputScope;
    private final Integer unitsPerPlate;
    private final int estimatedPlates;
    private final BigDecimal unitPrintHours;
    private final BigDecimal totalPrintHours;
    private final String extraCostsScope;
    private final Map<String, Asset> printersByKey;
    private final Map<String, Filament> filamentsByKey;
    private final BigDecimal precomputedFeePercent;
    private final BigDecimal precomputedFeeRate;
    private final QuoteAccumulator accumulator;

    public QuotingContext(
            QuoteInput input,
            Company company,
            int quantity,
            String inputScope,
            BigDecimal unitPrintHours,
            BigDecimal totalPrintHours,
            Map<String, Asset> printersByKey,
            Map<String, Filament> filamentsByKey,
            BigDecimal precomputedFeePercent,
            BigDecimal precomputedFeeRate,
            QuoteAccumulator accumulator) {
        this(
                input,
                company,
                quantity,
                inputScope,
                null,
                1,
                unitPrintHours,
                totalPrintHours,
                "per_order",
                printersByKey,
                filamentsByKey,
                precomputedFeePercent,
                precomputedFeeRate,
                accumulator
        );
    }

    public QuotingContext(
            QuoteInput input,
            Company company,
            int quantity,
            String inputScope,
            Integer unitsPerPlate,
            int estimatedPlates,
            BigDecimal unitPrintHours,
            BigDecimal totalPrintHours,
            String extraCostsScope,
            Map<String, Asset> printersByKey,
            Map<String, Filament> filamentsByKey,
            BigDecimal precomputedFeePercent,
            BigDecimal precomputedFeeRate,
            QuoteAccumulator accumulator) {
        this.input = input;
        this.company = company;
        this.quantity = quantity;
        this.inputScope = inputScope;
        this.unitsPerPlate = unitsPerPlate;
        this.estimatedPlates = Math.max(1, estimatedPlates);
        this.unitPrintHours = unitPrintHours;
        this.totalPrintHours = totalPrintHours;
        this.extraCostsScope = "per_unit".equalsIgnoreCase(extraCostsScope) ? "per_unit" : "per_order";
        this.printersByKey = printersByKey != null ? printersByKey : Map.of();
        this.filamentsByKey = filamentsByKey != null ? filamentsByKey : Map.of();
        this.precomputedFeePercent = precomputedFeePercent != null ? precomputedFeePercent : BigDecimal.ZERO;
        this.precomputedFeeRate = precomputedFeeRate != null ? precomputedFeeRate : BigDecimal.ZERO;
        this.accumulator = accumulator != null ? accumulator : new QuoteAccumulator();
    }

    public Asset findPrinter(String idOrGuid) {
        if (idOrGuid == null || idOrGuid.isBlank()) {
            return null;
        }
        return printersByKey.get(idOrGuid.trim());
    }

    public Filament findFilament(String idOrGuid) {
        if (idOrGuid == null || idOrGuid.isBlank()) {
            return null;
        }
        return filamentsByKey.get(idOrGuid.trim());
    }

    public BigDecimal unitAmount(BigDecimal totalAmount) {
        if (totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return QuoteConfigReader.divide(totalAmount, BigDecimal.valueOf(quantity));
    }

    public BigDecimal getMinOrderValue() {
        return company != null ? company.getMinOrderValue() : null;
    }
}
