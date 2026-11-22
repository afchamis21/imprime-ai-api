package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum SupportTicketCategory implements CodeAttribute {
    ORDER("ORDER"),
    DISPUTE("DISPUTE"),
    TECHNICAL("TECHNICAL"),
    PAYMENT("PAYMENT"),
    ACCOUNT("ACCOUNT"),
    OTHER("OTHER");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<SupportTicketCategory> {
        public Converter() {
            super(SupportTicketCategory.class);
        }
    }
}
