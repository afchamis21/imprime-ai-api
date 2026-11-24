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
public class AdditionalFeeQuotePartCalculator implements QuotePartCalculator {
    private static final String DEFAULT_FEE_LABEL = "Maquininha / Marketplace";

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.FEE_TAX;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        QuoteAccumulator acc = context.getAccumulator();
        if (!acc.isValid()) {
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
                    DEFAULT_FEE_LABEL,
                    Map.of("percent", BigDecimal.ZERO.setScale(2), "feeLabel", DEFAULT_FEE_LABEL)
            );
        }

        BigDecimal feePercent = extractFeePercent(effectiveConfig);
        String rawFeeLabel = QuoteConfigReader.getString(effectiveConfig, "feeLabel", DEFAULT_FEE_LABEL);
        String feeLabel = rawFeeLabel != null && !rawFeeLabel.isBlank() ? rawFeeLabel.trim() : DEFAULT_FEE_LABEL;

        BigDecimal netSellingPrice = QuoteConfigReader.sanitizeNonNegative(acc.getFinalSuggestedPriceTotal());
        BigDecimal feeRate = QuoteConfigReader.divide(feePercent, QuoteConfigReader.HUNDRED);
        BigDecimal feeTotalRaw = netSellingPrice.multiply(feeRate);
        BigDecimal unitFeeRaw = context.unitAmount(feeTotalRaw);

        acc.addFee(feeTotalRaw);

        String detailLabel = QuoteConfigReader.formatCompactPtBr(feePercent, 2) + "% · " + feeLabel;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("percent", QuoteConfigReader.scale2(feePercent));
        metadata.put("feeLabel", feeLabel);

        return new QuotePartResult(
                module.code(),
                module.title(),
                module.quoteType(),
                module.executionOrder(),
                module.enabled(),
                module.overridden(),
                QuoteConfigReader.scale2(unitFeeRaw),
                QuoteConfigReader.scale2(feeTotalRaw),
                feeTotalRaw,
                BigDecimal.ZERO.setScale(2),
                detailLabel,
                Map.copyOf(metadata)
        );
    }

    public static BigDecimal extractFeePercent(JsonNode effectiveConfig) {
        if (QuoteConfigReader.hasNonNullKey(effectiveConfig, "additionalFeePercent")) {
            return QuoteConfigReader.getNonNegativeBigDecimal(effectiveConfig, "additionalFeePercent", BigDecimal.ZERO);
        }
        return QuoteConfigReader.getNonNegativeBigDecimal(effectiveConfig, "feePercent", BigDecimal.ZERO);
    }
}
