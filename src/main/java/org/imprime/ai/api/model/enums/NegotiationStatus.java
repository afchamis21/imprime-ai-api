package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum NegotiationStatus implements CodeAttribute {
    OPEN("OPEN"),
    AWAITING_QUOTE("AWAITING_QUOTE"),
    QUOTE_RECEIVED("QUOTE_RECEIVED"),
    APPROVED("APPROVED"),
    IN_PRODUCTION("IN_PRODUCTION"),
    CLOSED("CLOSED"),
    CANCELLED("CANCELLED");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<NegotiationStatus> {
        public Converter() {
            super(NegotiationStatus.class);
        }
    }
}
