package org.imprime.ai.api.model.enums;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum LanguageCd implements CodeAttribute {
    PT_BR("pt-BR"),
    EN_US("en-US"),
    ES_ES("es-ES"),
    ;

    private final String code;

    @Nullable
    public static LanguageCd fromCode(String code) {
        for (LanguageCd languageCd : LanguageCd.values()) {
            if (languageCd.getCode().equals(code)) {
                return languageCd;
            }
        }

        return null;
    }

    public static class Converter extends CodeAttributeConverter<LanguageCd> {
        protected Converter() {
            super(LanguageCd.class);
        }
    }
}
