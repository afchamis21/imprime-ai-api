package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum PrinterKinematics implements CodeAttribute {
    COREXY("COREXY"),
    BED_SLINGER("BED_SLINGER"),
    DELTA("DELTA"),
    IDEX("IDEX"),
    RESIN_MSLA("RESIN_MSLA");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<PrinterKinematics> {
        public Converter() {
            super(PrinterKinematics.class);
        }
    }
}
