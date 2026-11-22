package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum UserRole implements CodeAttribute {
    BUYER("BUYER"),
    DESIGNER("DESIGNER"),
    MAKER("MAKER"),
    ADMIN("ADMIN");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<UserRole> {
        public Converter() {
            super(UserRole.class);
        }
    }
}
