package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum FilamentFinishType implements CodeAttribute {
    STANDARD("STANDARD"),
    MATTE("MATTE"),
    GLOSSY("GLOSSY"),
    SILK("SILK"),
    METALLIC("METALLIC"),
    TRANSLUCENT("TRANSLUCENT"),
    SPARKLE("SPARKLE"),
    MARBLE("MARBLE"),
    CARBON("CARBON"),
    WOOD("WOOD"),
    GLOW("GLOW");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<FilamentFinishType> {
        public Converter() {
            super(FilamentFinishType.class);
        }
    }
}
