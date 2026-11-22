package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum RefundStatus implements CodeAttribute {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    PROCESSED("PROCESSED"),
    FAILED("FAILED");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<RefundStatus> {
        public Converter() {
            super(RefundStatus.class);
        }
    }
}
