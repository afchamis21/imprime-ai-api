package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum SpoolType implements CodeAttribute {
    PLASTIC("PLASTIC"),
    CARDBOARD("CARDBOARD"),
    REFILL("REFILL"),
    REUSABLE("REUSABLE");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<SpoolType> {
        public Converter() {
            super(SpoolType.class);
        }
    }
}
