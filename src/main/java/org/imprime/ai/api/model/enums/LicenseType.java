package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum LicenseType implements CodeAttribute {
    PERSONAL("PERSONAL"),
    COMMERCIAL("COMMERCIAL"),
    CREATIVE_COMMONS("CREATIVE_COMMONS");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<LicenseType> {
        public Converter() {
            super(LicenseType.class);
        }
    }
}
