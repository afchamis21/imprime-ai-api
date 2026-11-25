package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum PrinterTechnology implements CodeAttribute {
    FDM("FDM"),
    SLA("SLA"),
    SLS("SLS"),
    DLP("DLP");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<PrinterTechnology> {
        public Converter() {
            super(PrinterTechnology.class);
        }
    }
}
