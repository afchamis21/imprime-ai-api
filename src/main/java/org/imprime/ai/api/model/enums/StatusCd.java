package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum StatusCd implements CodeAttribute {
    ACTIVE("A"),
    INACTIVE("I"),
    DELETED("D"),

    // Order Specific

    ;

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<StatusCd> {
        public Converter() {
            super(StatusCd.class);
        }
    }
}