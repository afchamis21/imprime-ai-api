package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum FavoriteTargetType implements CodeAttribute {
    MAKER("MAKER"),
    PRODUCT("PRODUCT"),
    MODEL_3D("MODEL_3D");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<FavoriteTargetType> {
        public Converter() {
            super(FavoriteTargetType.class);
        }
    }
}
