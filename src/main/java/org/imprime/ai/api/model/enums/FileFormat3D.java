package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.base.CodeAttributeConverter;

@Getter
@RequiredArgsConstructor
public enum FileFormat3D implements CodeAttribute {
    STL("STL"),
    THREE_MF("3MF"),
    OBJ("OBJ"),
    GLB("GLB"),
    GCODE("GCODE"),
    SCAD("SCAD");

    private final String code;

    @jakarta.persistence.Converter
    public static class Converter extends CodeAttributeConverter<FileFormat3D> {
        public Converter() {
            super(FileFormat3D.class);
        }
    }
}
