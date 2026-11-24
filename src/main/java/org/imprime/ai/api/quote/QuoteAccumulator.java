package org.imprime.ai.api.quote;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class QuoteAccumulator {
    private BigDecimal materialCostTotal = BigDecimal.ZERO;
    private BigDecimal energyCostTotal = BigDecimal.ZERO;
    private BigDecimal depreciationCostTotal = BigDecimal.ZERO;
    private BigDecimal directPrintCostTotal = BigDecimal.ZERO;
    private BigDecimal baseProductionCostTotal = BigDecimal.ZERO;

    private BigDecimal failureReserveCostTotal = BigDecimal.ZERO;
    private BigDecimal costModifiersTotal = BigDecimal.ZERO;

    private BigDecimal laborCostTotal = BigDecimal.ZERO;
    private BigDecimal packagingCostTotal = BigDecimal.ZERO;
    private BigDecimal extraCostsTotal = BigDecimal.ZERO;

    private BigDecimal grossPriceBeforeDiscountTotal = BigDecimal.ZERO;
    private BigDecimal discountTotal = BigDecimal.ZERO;
    private BigDecimal feesCostTotal = BigDecimal.ZERO;
    private BigDecimal finalSuggestedPriceTotal = BigDecimal.ZERO;

    private boolean valid = true;
    private boolean profitApplied = false;

    private BigDecimal unitWeightGrams = BigDecimal.ZERO;
    private BigDecimal totalWeightGrams = BigDecimal.ZERO;
    private BigDecimal powerWatts = BigDecimal.ZERO;
    private String powerSource = "fallback";
    private BigDecimal unitEnergyKwh = BigDecimal.ZERO;
    private BigDecimal totalEnergyKwh = BigDecimal.ZERO;
    private BigDecimal hourlyDepreciationRate = BigDecimal.ZERO;
    private String depreciationSource = "settings_rate";
    private boolean requiresLabor = false;
    private int effectiveLaborTimeMinutes = 0;
    private BigDecimal failureRatePercent = BigDecimal.ZERO;
    private BigDecimal profitMarginPercent = BigDecimal.ZERO;
    private BigDecimal additionalFeePercent = BigDecimal.ZERO;
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private String activeProfitStrategy;

    private final List<QuotePartResult> parts = new ArrayList<>();
    private final List<QuoteCalculationResult.FilamentBreakdown> filaments = new ArrayList<>();
    private final List<QuoteValidationWarning> warnings = new ArrayList<>();
    private final Set<String> emittedWarningCodes = new HashSet<>();

    public BigDecimal getProductionCostTotal() {
        return baseProductionCostTotal.add(costModifiersTotal);
    }

    public void addDirectPrintCost(BigDecimal amount) {
        if (amount == null) {
            return;
        }
        directPrintCostTotal = directPrintCostTotal.add(amount);
        baseProductionCostTotal = baseProductionCostTotal.add(amount);
    }

    public void addBaseProductionCost(BigDecimal amount) {
        if (amount == null) {
            return;
        }
        baseProductionCostTotal = baseProductionCostTotal.add(amount);
    }

    public void addCostModifier(BigDecimal amount) {
        if (amount == null) {
            return;
        }
        costModifiersTotal = costModifiersTotal.add(amount);
    }

    public void addDiscount(BigDecimal amount, BigDecimal percentApplied) {
        if (amount != null) {
            discountTotal = discountTotal.add(amount);
        }
        if (percentApplied != null) {
            discountPercent = discountPercent.add(percentApplied);
        }
    }

    public void addFee(BigDecimal amount) {
        if (amount != null) {
            feesCostTotal = feesCostTotal.add(amount);
        }
    }

    public void markInvalid() {
        this.valid = false;
        this.grossPriceBeforeDiscountTotal = BigDecimal.ZERO;
        this.discountTotal = BigDecimal.ZERO;
        this.feesCostTotal = BigDecimal.ZERO;
        this.finalSuggestedPriceTotal = BigDecimal.ZERO;
    }

    public void addPart(QuotePartResult part) {
        if (part != null) {
            parts.add(part);
        }
    }

    public void addWarning(QuoteValidationWarning warning) {
        if (warning != null) {
            warnings.add(warning);
        }
    }

    public void addWarningOnce(QuoteValidationWarning warning) {
        if (warning != null && emittedWarningCodes.add(warning.code())) {
            warnings.add(warning);
        }
    }
}
