package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ScadParameterType implements CodeAttribute {
    NUMBER("NUMBER"),
    BOOLEAN("BOOLEAN"),
    STRING("STRING"),
    SELECT("SELECT");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ScadParameterType> {
        public Converter() {
            super(ScadParameterType.class);
        }
    }
}
