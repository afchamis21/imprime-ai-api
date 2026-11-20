package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum StockAdjustmentReason implements CodeAttribute {
    PURCHASE("PURCHASE"),
    PRINT_CONSUMPTION("PRINT_CONSUMPTION"),
    FAILED_PRINT("FAILED_PRINT"),
    MANUAL_CORRECTION("MANUAL_CORRECTION"),
    CALIBRATION("CALIBRATION");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<StockAdjustmentReason> {
        public Converter() {
            super(StockAdjustmentReason.class);
        }
    }
}
