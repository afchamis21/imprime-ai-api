package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum QuoteModuleType implements CodeAttribute {
    BASE_COST("BASE_COST", 10),
    COST_MODIFIER("COST_MODIFIER", 20),
    PROFIT("PROFIT", 30),
    DISCOUNT_PROMOTION("DISCOUNT_PROMOTION", 40),
    FEE_TAX("FEE_TAX", 50);

    private final String code;
    private final int phaseOrder;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<QuoteModuleType> {
        public Converter() {
            super(QuoteModuleType.class);
        }
    }
}
