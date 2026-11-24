package org.imprime.ai.api.quote.calculator;

import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.QuoteAccumulator;
import org.imprime.ai.api.quote.QuoteConfigReader;
import org.imprime.ai.api.quote.QuotePartCalculator;
import org.imprime.ai.api.quote.QuotePartResult;
import org.imprime.ai.api.quote.QuotingContext;
import org.imprime.ai.api.quote.ResolvedQuoteModule;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class PackagingQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_PACKAGING_COST = new BigDecimal("5.00");

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.BASE_COST;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        BigDecimal totalCostRaw = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "fixedPackagingCost", DEFAULT_PACKAGING_COST);
        BigDecimal unitCostRaw = context.unitAmount(totalCostRaw);

        QuoteAccumulator acc = context.getAccumulator();
        acc.setPackagingCostTotal(totalCostRaw);
        acc.addBaseProductionCost(totalCostRaw);

        return new QuotePartResult(
                module.code(),
                module.title(),
                module.quoteType(),
                module.executionOrder(),
                module.enabled(),
                module.overridden(),
                QuoteConfigReader.scale2(unitCostRaw),
                QuoteConfigReader.scale2(totalCostRaw),
                totalCostRaw,
                BigDecimal.ZERO.setScale(2),
                QuoteConfigReader.formatBrl(totalCostRaw) + " por pedido",
                Map.of("fixedPackagingCost", QuoteConfigReader.scale2(totalCostRaw))
        );
    }
}
