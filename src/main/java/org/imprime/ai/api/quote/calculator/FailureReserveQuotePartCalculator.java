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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FailureReserveQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_FAILURE_RATE_PERCENT = new BigDecimal("10.00");

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.COST_MODIFIER;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        BigDecimal failureRatePercent = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "failureRatePercent", DEFAULT_FAILURE_RATE_PERCENT);
        String applyOn = QuoteConfigReader.getString(effectiveConfig, "applyOn", "DIRECT_PRINT_COST");

        QuoteAccumulator acc = context.getAccumulator();
        boolean useBaseProduction = "BASE_PRODUCTION_COST".equalsIgnoreCase(applyOn);
        BigDecimal base = useBaseProduction
                ? acc.getBaseProductionCostTotal()
                : acc.getDirectPrintCostTotal();

        BigDecimal rate = QuoteConfigReader.divide(failureRatePercent, QuoteConfigReader.HUNDRED);
        BigDecimal totalCostRaw = base.multiply(rate);
        BigDecimal unitCostRaw = context.unitAmount(totalCostRaw);

        acc.setFailureRatePercent(failureRatePercent);
        acc.setFailureReserveCostTotal(totalCostRaw);
        acc.addCostModifier(totalCostRaw);

        String baseLabel = useBaseProduction ? "sobre custo base" : "sobre custo direto";
        String detailLabel = QuoteConfigReader.formatCompactPtBr(failureRatePercent, 2) + "% " + baseLabel;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("percent", QuoteConfigReader.scale2(failureRatePercent));
        metadata.put("applyOn", useBaseProduction ? "BASE_PRODUCTION_COST" : "DIRECT_PRINT_COST");

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
                detailLabel,
                Map.copyOf(metadata)
        );
    }
}
