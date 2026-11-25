package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum PrinterConnectivity implements CodeAttribute {
    WIFI("WIFI"),
    LAN("LAN"),
    USB("USB"),
    SD_CARD("SD_CARD"),
    CLOUD("CLOUD"),
    BLUETOOTH("BLUETOOTH");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<PrinterConnectivity> {
        public Converter() {
            super(PrinterConnectivity.class);
        }
    }
}
