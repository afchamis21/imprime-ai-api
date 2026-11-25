package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ReportReason implements CodeAttribute {
    COPYRIGHT_DMCA("COPYRIGHT_DMCA"),
    INAPPROPRIATE_CONTENT("INAPPROPRIATE_CONTENT"),
    SCAM_FRAUD("SCAM_FRAUD"),
    MISLEADING_INFO("MISLEADING_INFO"),
    COUNTERFEIT_OR_ILLEGAL("COUNTERFEIT_OR_ILLEGAL"),
    LOW_QUALITY_OR_DEFECTIVE("LOW_QUALITY_OR_DEFECTIVE"),
    HARASSMENT_OR_ABUSE("HARASSMENT_OR_ABUSE"),
    SPAM("SPAM"),
    OTHER("OTHER");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ReportReason> {
        public Converter() {
            super(ReportReason.class);
        }
    }
}
