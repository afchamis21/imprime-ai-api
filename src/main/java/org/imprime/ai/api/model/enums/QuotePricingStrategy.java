package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum QuotePricingStrategy implements CodeAttribute {
    MARKUP("MARKUP"),
    MARGIN_ON_PRICE("MARGIN_ON_PRICE");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<QuotePricingStrategy> {
        public Converter() {
            super(QuotePricingStrategy.class);
        }
    }
}
