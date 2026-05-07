package org.imprime.ai.api.model.enums;

import jakarta.persistence.AttributeConverter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum StatusCd implements CodeAttribute {
    ACTIVE("A"),
    INACTIVE("I"),
    DELETED("D")
    ;

    private final String code;

    public static class Converter extends CodeAttributeConverter<StatusCd> {
        public Converter() {
            super(StatusCd.class);
        }
    }
}