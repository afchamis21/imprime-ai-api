package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum RefundType implements CodeAttribute {
    FULL("FULL"),
    PARTIAL("PARTIAL");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<RefundType> {
        public Converter() {
            super(RefundType.class);
        }
    }
}
