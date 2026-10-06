package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum EntityType implements CodeAttribute {
    USER("U"),
    COMPANY("C"),
    ORDER("O");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<EntityType> {
        public Converter() {
            super(EntityType.class);
        }
    }

}
