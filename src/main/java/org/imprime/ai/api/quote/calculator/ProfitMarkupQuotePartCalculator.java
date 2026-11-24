package org.imprime.ai.api.quote.calculator;

import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.QuoteAccumulator;
import org.imprime.ai.api.quote.QuoteConfigReader;
import org.imprime.ai.api.quote.QuotePartCalculator;
import org.imprime.ai.api.quote.QuotePartResult;
import org.imprime.ai.api.quote.QuoteValidationWarning;
import org.imprime.ai.api.quote.QuotingContext;
import org.imprime.ai.api.quote.ResolvedQuoteModule;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProfitMarkupQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_MARKUP_PERCENT = new BigDecimal("50.00");

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.PROFIT;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        BigDecimal profitPercent = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "profitMarginPercent", DEFAULT_MARKUP_PERCENT);

        QuoteAccumulator acc = context.getAccumulator();
        acc.setActiveProfitStrategy(module.code());
        acc.setProfitMarginPercent(profitPercent);
        acc.setProfitApplied(true);

        BigDecimal feePercent = context.getPrecomputedFeePercent();
        BigDecimal feeRate = context.getPrecomputedFeeRate();

        if (feePercent.compareTo(QuoteConfigReader.MAX_COMBINED_PERCENT) >= 0) {
            acc.markInvalid();
            acc.addWarningOnce(new QuoteValidationWarning(
                    "INVALID_MARGIN_AND_FEE_SUM",
                    "error",
                    "As taxas adicionais devem ser inferiores a 95%."
            ));
            return invalidResult(module, profitPercent);
        }

        BigDecimal m = QuoteConfigReader.divide(profitPercent, QuoteConfigReader.HUNDRED);
        BigDecimal productionCost = acc.getProductionCostTotal();
        BigDecimal grossBeforeDiscount = QuoteConfigReader.divide(
                productionCost.multiply(BigDecimal.ONE.add(m)),
                BigDecimal.ONE.subtract(feeRate)
        );
        BigDecimal profitTotalRaw = productionCost.multiply(m);
        BigDecimal unitProfitRaw = context.unitAmount(profitTotalRaw);

        acc.setGrossPriceBeforeDiscountTotal(grossBeforeDiscount);

        String detailLabel = QuoteConfigReader.formatCompactPtBr(profitPercent, 2) + "% markup s/ custo";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("percent", QuoteConfigReader.scale2(profitPercent));
        metadata.put("strategy", "MARKUP");

        return new QuotePartResult(
                module.code(),
                module.title(),
                module.quoteType(),
                module.executionOrder(),
                module.enabled(),
                module.overridden(),
                QuoteConfigReader.scale2(unitProfitRaw),
                QuoteConfigReader.scale2(profitTotalRaw),
                profitTotalRaw,
                BigDecimal.ZERO.setScale(2),
                detailLabel,
                Map.copyOf(metadata)
        );
    }

    private QuotePartResult invalidResult(ResolvedQuoteModule module, BigDecimal profitPercent) {
        return new QuotePartResult(
                module.code(),
                module.title(),
                module.quoteType(),
                module.executionOrder(),
                module.enabled(),
                module.overridden(),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO,
                BigDecimal.ZERO.setScale(2),
                QuoteConfigReader.formatCompactPtBr(profitPercent, 2) + "% markup s/ custo",
                Map.of("percent", QuoteConfigReader.scale2(profitPercent), "strategy", "MARKUP")
        );
    }
}
