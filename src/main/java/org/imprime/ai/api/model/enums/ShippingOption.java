package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ShippingOption implements CodeAttribute {
    LOCAL_PICKUP("LOCAL_PICKUP"),
    CORREIOS("CORREIOS"),
    CARRIER("CARRIER"),
    LOCAL_COURIER("LOCAL_COURIER");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ShippingOption> {
        public Converter() {
            super(ShippingOption.class);
        }
    }
}
