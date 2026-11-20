package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ModelSourceProvider implements CodeAttribute {
    IMPRIME_AI("IMPRIME_AI"),
    MAKERWORLD("MAKERWORLD"),
    PRINTABLES("PRINTABLES"),
    THINGIVERSE("THINGIVERSE"),
    CULTS3D("CULTS3D"),
    LOCAL_FOLDER("LOCAL_FOLDER"),
    CUSTOM("CUSTOM");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ModelSourceProvider> {
        public Converter() {
            super(ModelSourceProvider.class);
        }
    }
}
