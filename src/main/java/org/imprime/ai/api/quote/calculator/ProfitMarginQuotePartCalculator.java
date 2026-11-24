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
public class ProfitMarginQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_MARGIN_PERCENT = new BigDecimal("35.00");

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
                effectiveConfig, "profitMarginPercent", DEFAULT_MARGIN_PERCENT);

        QuoteAccumulator acc = context.getAccumulator();
        acc.setActiveProfitStrategy(module.code());
        acc.setProfitMarginPercent(profitPercent);
        acc.setProfitApplied(true);

        BigDecimal feePercent = context.getPrecomputedFeePercent();
        BigDecimal feeRate = context.getPrecomputedFeeRate();

        if (profitPercent.add(feePercent).compareTo(QuoteConfigReader.MAX_COMBINED_PERCENT) >= 0) {
            acc.markInvalid();
            acc.addWarningOnce(new QuoteValidationWarning(
                    "INVALID_MARGIN_AND_FEE_SUM",
                    "error",
                    "Na estratégia de margem sobre venda, a soma da margem com as taxas adicionais deve ser menor que 95%."
            ));
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
                    QuoteConfigReader.formatCompactPtBr(profitPercent, 2) + "% margem s/ venda",
                    Map.of("percent", QuoteConfigReader.scale2(profitPercent), "strategy", "MARGIN_ON_PRICE")
            );
        }

        BigDecimal m = QuoteConfigReader.divide(profitPercent, QuoteConfigReader.HUNDRED);
        BigDecimal productionCost = acc.getProductionCostTotal();
        BigDecimal denominator = BigDecimal.ONE.subtract(m).subtract(feeRate);
        BigDecimal grossBeforeDiscount = QuoteConfigReader.divide(productionCost, denominator);
        BigDecimal profitTotalRaw = grossBeforeDiscount.multiply(m);
        BigDecimal unitProfitRaw = context.unitAmount(profitTotalRaw);

        acc.setGrossPriceBeforeDiscountTotal(grossBeforeDiscount);

        String detailLabel = QuoteConfigReader.formatCompactPtBr(profitPercent, 2) + "% margem s/ venda";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("percent", QuoteConfigReader.scale2(profitPercent));
        metadata.put("strategy", "MARGIN_ON_PRICE");

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
}
