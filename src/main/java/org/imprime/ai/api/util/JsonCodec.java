package org.imprime.ai.api.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class JsonCodec {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public JsonCodec() {
        this(JsonMapper.builder().build());
    }

    @Autowired
    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }

    public String toJsonArray(List<?> values) {
        if (values == null) {
            return "[]";
        }
        return objectMapper.writeValueAsString(values);
    }

    public String toEnumArrayJson(List<? extends Enum<?>> values) {
        if (values == null) {
            return "[]";
        }
        List<String> names = values.stream()
                .filter(Objects::nonNull)
                .map(Enum::name)
                .toList();
        return objectMapper.writeValueAsString(names);
    }

    public List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, STRING_LIST_TYPE);
            return parsed != null ? parsed : List.of();
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON string list: {}", e.getMessage());
            return List.of();
        }
    }

    public <E extends Enum<E>> List<E> readEnumList(String json, Class<E> enumType) {
        List<String> rawValues = readStringList(json);
        if (rawValues.isEmpty()) {
            return List.of();
        }
        List<E> result = new ArrayList<>(rawValues.size());
        for (String raw : rawValues) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                result.add(Enum.valueOf(enumType, raw.trim()));
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring unknown {} enum value '{}' in JSONB array", enumType.getSimpleName(), raw);
            }
        }
        return result;
    }

    public <T> T readObject(String json, Class<T> targetType) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, targetType);
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON object as {}: {}", targetType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    public <T> List<T> readList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            List<T> parsed = objectMapper.readValue(json, listType);
            return parsed != null ? parsed : List.of();
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON list of {}: {}", elementType.getSimpleName(), e.getMessage());
            return List.of();
        }
    }
}
