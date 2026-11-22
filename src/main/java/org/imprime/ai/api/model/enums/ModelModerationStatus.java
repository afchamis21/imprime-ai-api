package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ModelModerationStatus implements CodeAttribute {
    PENDING_REVIEW("PENDING_REVIEW"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    TAKEDOWN_DMCA("TAKEDOWN_DMCA");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ModelModerationStatus> {
        public Converter() {
            super(ModelModerationStatus.class);
        }
    }
}
