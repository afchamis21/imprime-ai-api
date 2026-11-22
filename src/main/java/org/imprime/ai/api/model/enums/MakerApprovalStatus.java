package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum MakerApprovalStatus implements CodeAttribute {
    PENDING_APPROVAL("PENDING_APPROVAL"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    SUSPENDED("SUSPENDED");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<MakerApprovalStatus> {
        public Converter() {
            super(MakerApprovalStatus.class);
        }
    }
}
