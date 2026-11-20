package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ReviewTargetType implements CodeAttribute {
    PRODUCT("PRODUCT"),
    MODEL_3D("MODEL_3D"),
    MAKER("MAKER");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ReviewTargetType> {
        public Converter() {
            super(ReviewTargetType.class);
        }
    }
}
