package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum QuoteHistoryStatus implements CodeAttribute {
    DRAFT("DRAFT"),
    SENT("SENT"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<QuoteHistoryStatus> {
        public Converter() {
            super(QuoteHistoryStatus.class);
        }
    }
}
