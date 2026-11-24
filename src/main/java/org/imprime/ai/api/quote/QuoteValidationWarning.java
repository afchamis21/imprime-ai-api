package org.imprime.ai.api.quote;

public record QuoteValidationWarning(
        String code,
        String severity,
        String message
) {
}
