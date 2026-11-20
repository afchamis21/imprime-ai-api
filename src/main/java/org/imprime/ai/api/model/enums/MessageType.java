package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum MessageType implements CodeAttribute {
    MESSAGE("M"),
    OFFER("O"),
    SYSTEM("SYS"),
    ATTACHMENT("ATT");

    private final String code;

    public static class Converter extends CodeAttributeConverter<MessageType> {
        public Converter() {
            super(MessageType.class);
        }
    }
}
