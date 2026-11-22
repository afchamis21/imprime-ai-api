package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum TicketPriority implements CodeAttribute {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    URGENT("URGENT");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<TicketPriority> {
        public Converter() {
            super(TicketPriority.class);
        }
    }
}
