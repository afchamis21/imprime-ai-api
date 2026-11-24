package org.imprime.ai.api.quote;

import org.imprime.ai.api.model.enums.QuoteModuleType;

import java.math.BigDecimal;
import java.util.Map;

public record QuotePartResult(
        String code,
        String title,
        QuoteModuleType quoteType,
        int executionOrder,
        boolean enabled,
        boolean overridden,
        BigDecimal unitAmount,
        BigDecimal totalAmount,
        BigDecimal rawTotalAmount,
        BigDecimal shareOfProductionCostPercent,
        String detailLabel,
        Map<String, Object> metadata
) {
    public QuotePartResult withShareOfProductionCostPercent(BigDecimal share) {
        return new QuotePartResult(
                code,
                title,
                quoteType,
                executionOrder,
                enabled,
                overridden,
                unitAmount,
                totalAmount,
                rawTotalAmount,
                share,
                detailLabel,
                metadata
        );
    }
}
