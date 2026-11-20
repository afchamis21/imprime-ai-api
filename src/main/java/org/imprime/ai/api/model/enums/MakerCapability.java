package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum MakerCapability implements CodeAttribute {
    FDM("FDM"),
    SLA_RESIN("SLA_RESIN"),
    MULTICOLOR_AMS("MULTICOLOR_AMS"),
    PAINTING("PAINTING"),
    MODELING("MODELING"),
    POST_PROCESSING("POST_PROCESSING"),
    SMALL_BATCH("SMALL_BATCH");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<MakerCapability> {
        public Converter() {
            super(MakerCapability.class);
        }
    }
}
