package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ExtruderType implements CodeAttribute {
    DIRECT_DRIVE("DIRECT_DRIVE"),
    BOWDEN("BOWDEN"),
    NONE("NONE");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ExtruderType> {
        public Converter() {
            super(ExtruderType.class);
        }
    }
}
