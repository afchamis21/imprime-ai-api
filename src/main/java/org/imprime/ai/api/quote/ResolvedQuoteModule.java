package org.imprime.ai.api.quote;

import org.imprime.ai.api.model.enums.QuoteModuleType;
import tools.jackson.databind.JsonNode;

public record ResolvedQuoteModule(
        Long catalogId,
        String code,
        String title,
        String description,
        QuoteModuleType quoteType,
        int phaseOrder,
        int executionOrder,
        String calculatorClass,
        boolean enabled,
        boolean canDisable,
        boolean allowQuoteOverride,
        String mutualExclusionGroup,
        boolean overridden,
        JsonNode effectiveConfig
) {
    public ResolvedQuoteModule withEnabled(boolean nextEnabled, boolean nextOverridden) {
        return new ResolvedQuoteModule(
                catalogId,
                code,
                title,
                description,
                quoteType,
                phaseOrder,
                executionOrder,
                calculatorClass,
                nextEnabled,
                canDisable,
                allowQuoteOverride,
                mutualExclusionGroup,
                nextOverridden,
                effectiveConfig
        );
    }
}
