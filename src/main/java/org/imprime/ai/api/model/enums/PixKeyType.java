package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum PixKeyType implements CodeAttribute {
    CPF("CPF"),
    CNPJ("CNPJ"),
    EMAIL("EMAIL"),
    PHONE("PHONE"),
    RANDOM("RANDOM");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<PixKeyType> {
        public Converter() {
            super(PixKeyType.class);
        }
    }
}
