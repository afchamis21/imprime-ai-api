package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum QueuePriority implements CodeAttribute {
    LOW("LOW"),
    NORMAL("NORMAL"),
    HIGH("HIGH"),
    URGENT("URGENT");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<QueuePriority> {
        public Converter() {
            super(QueuePriority.class);
        }
    }
}
