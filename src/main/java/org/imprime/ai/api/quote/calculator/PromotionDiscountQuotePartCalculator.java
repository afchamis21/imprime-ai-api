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
public class PromotionDiscountQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_MAX_DISCOUNT = new BigDecimal("100.00");

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.DISCOUNT_PROMOTION;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        QuoteAccumulator acc = context.getAccumulator();
        if (!acc.isValid()) {
            return zeroResult(module);
        }

        BigDecimal requestedRaw = QuoteConfigReader.hasNonNullKey(effectiveConfig, "discountPercent")
                ? QuoteConfigReader.getNonNegativeBigDecimal(effectiveConfig, "discountPercent", BigDecimal.ZERO)
                : QuoteConfigReader.getNonNegativeBigDecimal(effectiveConfig, "defaultDiscountPercent", BigDecimal.ZERO);

        BigDecimal maxDiscountPercent = QuoteConfigReader.clampPercent(
                QuoteConfigReader.getNonNegativeBigDecimal(effectiveConfig, "maxDiscountPercent", DEFAULT_MAX_DISCOUNT)
        );
        String campaignLabel = QuoteConfigReader.getString(effectiveConfig, "campaignLabel", "");
        String trimmedCampaign = campaignLabel != null ? campaignLabel.trim() : "";

        BigDecimal requestedClamped = QuoteConfigReader.clampPercent(requestedRaw);
        BigDecimal effectivePercent = requestedClamped.compareTo(maxDiscountPercent) > 0
                ? maxDiscountPercent
                : requestedClamped;

        if (requestedClamped.compareTo(maxDiscountPercent) > 0) {
            acc.addWarningOnce(new QuoteValidationWarning(
                    "DISCOUNT_CAPPED_AT_MAX",
                    "warning",
                    "Desconto limitado ao teto configurado de "
                            + QuoteConfigReader.formatCompactPtBr(maxDiscountPercent, 2) + "%."
            ));
        }

        BigDecimal discountRate = QuoteConfigReader.divide(effectivePercent, QuoteConfigReader.HUNDRED);
        BigDecimal discountTotalRaw = acc.getGrossPriceBeforeDiscountTotal().multiply(discountRate);
        BigDecimal unitDiscountRaw = context.unitAmount(discountTotalRaw);

        acc.addDiscount(discountTotalRaw, effectivePercent);

        BigDecimal negativeTotalRaw = QuoteConfigReader.negateSafe(discountTotalRaw);
        BigDecimal negativeUnitRaw = QuoteConfigReader.negateSafe(unitDiscountRaw);

        String detailLabel;
        if (effectivePercent.compareTo(BigDecimal.ZERO) == 0) {
            detailLabel = trimmedCampaign.isBlank() ? "Sem desconto" : "0% (" + trimmedCampaign + ")";
        } else {
            String base = "-" + QuoteConfigReader.formatCompactPtBr(effectivePercent, 2) + "%";
            detailLabel = trimmedCampaign.isBlank() ? base : base + " (" + trimmedCampaign + ")";
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("percent", QuoteConfigReader.scale2(effectivePercent));
        metadata.put("campaignLabel", trimmedCampaign);

        return new QuotePartResult(
                module.code(),
                module.title(),
                module.quoteType(),
                module.executionOrder(),
                module.enabled(),
                module.overridden(),
                QuoteConfigReader.scale2(negativeUnitRaw),
                QuoteConfigReader.scale2(negativeTotalRaw),
                negativeTotalRaw,
                BigDecimal.ZERO.setScale(2),
                detailLabel,
                Map.copyOf(metadata)
        );
    }

    private QuotePartResult zeroResult(ResolvedQuoteModule module) {
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
                "Sem desconto",
                Map.of("percent", BigDecimal.ZERO.setScale(2), "campaignLabel", "")
        );
    }
}
