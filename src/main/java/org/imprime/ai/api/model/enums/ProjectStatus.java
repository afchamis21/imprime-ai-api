package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum ProjectStatus implements CodeAttribute {
    PLANNING("PLANNING"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    ON_HOLD("ON_HOLD"),
    CANCELLED("CANCELLED");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<ProjectStatus> {
        public Converter() {
            super(ProjectStatus.class);
        }
    }
}
