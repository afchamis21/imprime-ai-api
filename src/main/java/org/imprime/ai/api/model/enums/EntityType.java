package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum EntityType implements CodeAttribute {
    USER("U"),
    COMPANY("C"),
    ORDER("O");

    private final String code;

    public static class Converter extends CodeAttributeConverter<EntityType> {
        protected Converter() {
            super(EntityType.class);
        }
    }

}
