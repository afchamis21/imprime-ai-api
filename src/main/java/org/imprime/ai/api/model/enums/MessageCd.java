package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum MessageCd implements CodeAttribute {
    INTERNAL_SERVER_ERROR("SYS_500"),

    UNAUTHORIZED("AUTH_401");

    private final String code;

    public static class Converter extends CodeAttributeConverter<MessageCd> {
        public Converter() {
            super(MessageCd.class);
        }
    }
}
