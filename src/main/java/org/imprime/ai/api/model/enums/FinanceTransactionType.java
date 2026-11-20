package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum FinanceTransactionType implements CodeAttribute {
    SALE("SALE"),
    PAYOUT("PAYOUT"),
    FILAMENT("FILAMENT"),
    ENERGY("ENERGY"),
    MAINTENANCE("MAINTENANCE"),
    PLATFORM_FEE("PLATFORM_FEE"),
    REFUND("REFUND");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<FinanceTransactionType> {
        public Converter() {
            super(FinanceTransactionType.class);
        }
    }
}
