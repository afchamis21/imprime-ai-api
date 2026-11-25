package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ReportStatus implements CodeAttribute {
    OPEN("OPEN"),
    UNDER_REVIEW("UNDER_REVIEW"),
    RESOLVED("RESOLVED"),
    DISMISSED("DISMISSED");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ReportStatus> {
        public Converter() {
            super(ReportStatus.class);
        }
    }
}
