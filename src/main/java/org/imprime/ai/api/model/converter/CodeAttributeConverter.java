package org.imprime.ai.api.model.converter;

import jakarta.persistence.AttributeConverter;
import org.imprime.ai.api.model.enums.CodeAttribute;

public abstract class CodeAttributeConverter<T extends Enum<T> & CodeAttribute > implements AttributeConverter<T,String> {
    private final Class<T> enumType;

    protected CodeAttributeConverter(Class<T> enumType) {
        if (enumType == null) {
            throw new IllegalArgumentException("enumType must not be null");
        }

        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }

        return attribute.getCode();
    }

    @Override
    public T convertToEntityAttribute(String code) {
        if (code == null) {
            return null;
        }

        for (T value: enumType.getEnumConstants()) {
            if (code.equals(value.getCode())) {
                return value;
            }
        }

        return null;
    }
}
