package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum QuoteDepreciationMode implements CodeAttribute {
    FIXED_HOURLY("FIXED_HOURLY"),
    MACHINE_LIFECYCLE("MACHINE_LIFECYCLE");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<QuoteDepreciationMode> {
        public Converter() {
            super(QuoteDepreciationMode.class);
        }
    }
}
