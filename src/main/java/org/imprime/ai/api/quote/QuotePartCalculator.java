package org.imprime.ai.api.quote;

import org.imprime.ai.api.model.enums.QuoteModuleType;
import tools.jackson.databind.JsonNode;

public interface QuotePartCalculator {
    default String calculatorKey() {
        return getClass().getName();
    }

    QuoteModuleType supportedType();

    QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module
    );
}
