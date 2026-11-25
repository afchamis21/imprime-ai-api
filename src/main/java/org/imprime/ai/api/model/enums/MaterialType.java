package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum MaterialType implements CodeAttribute {
    PLA("PLA"),
    PETG("PETG"),
    ABS("ABS"),
    ASA("ASA"),
    TPU("TPU"),
    RESIN("RESIN"),
    NYLON("NYLON"),
    CARBON_FIBER("CARBON_FIBER"),
    PC("PC"),
    PETG_CF("PETG_CF"),
    PA_CF("PA_CF"),
    PVA("PVA"),
    HIPS("HIPS"),
    OTHER("OTHER");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<MaterialType> {
        public Converter() {
            super(MaterialType.class);
        }
    }
}
