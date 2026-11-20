package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ModelSourceStatus implements CodeAttribute {
    CONNECTED("CONNECTED"),
    SYNCING("SYNCING"),
    PAUSED("PAUSED"),
    ERROR("ERROR");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ModelSourceStatus> {
        public Converter() {
            super(ModelSourceStatus.class);
        }
    }
}
