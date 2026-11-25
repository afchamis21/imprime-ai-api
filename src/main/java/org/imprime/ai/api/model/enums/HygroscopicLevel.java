package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum HygroscopicLevel implements CodeAttribute {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<HygroscopicLevel> {
        public Converter() {
            super(HygroscopicLevel.class);
        }
    }
}
