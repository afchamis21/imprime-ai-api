package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum PrinterStatus implements CodeAttribute {
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE"),
    PRINTING("PRINTING"),
    PAUSED("PAUSED"),
    MAINTENANCE("MAINTENANCE"),
    ERROR("ERROR");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<PrinterStatus> {
        public Converter() {
            super(PrinterStatus.class);
        }
    }
}
