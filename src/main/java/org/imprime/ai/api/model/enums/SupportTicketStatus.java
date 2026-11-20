package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum SupportTicketStatus implements CodeAttribute {
    OPEN("OPEN"),
    IN_REVIEW("IN_REVIEW"),
    WAITING_USER("WAITING_USER"),
    RESOLVED("RESOLVED"),
    CLOSED("CLOSED");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<SupportTicketStatus> {
        public Converter() {
            super(SupportTicketStatus.class);
        }
    }
}
