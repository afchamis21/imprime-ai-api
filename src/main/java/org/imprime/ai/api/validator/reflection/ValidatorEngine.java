package org.imprime.ai.api.validator.reflection;

import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.IValidator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ValidatorEngine {

    private static final Map<Class<?>, List<FieldValidator>> CLASS_VALIDATION_CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static MessageCd validate(@NonNull Object object) throws Exception {
        Class<?> clazz = object.getClass();

        List<FieldValidator> validationPlan = CLASS_VALIDATION_CACHE.computeIfAbsent(clazz, ValidatorEngine::buildValidationPlan);

        for (FieldValidator fieldValidator : validationPlan) {
            Object value = fieldValidator.field.get(object);

            for (IValidator<Object> validator : fieldValidator.validators) {
                MessageCd error = validator.validate(value);
                if (error != null) {
                    return error;
                }
            }
        }

        return null;
    }

    private static List<FieldValidator> buildValidationPlan(Class<?> clazz) {
        List<FieldValidator> plan = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            List<IValidator<Object>> validatorsForField = new ArrayList<>();

            for (Annotation annotation : field.getAnnotations()) {
                Constraint constraint = annotation.annotationType().getAnnotation(Constraint.class);
                if (constraint != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        AnnotationValidator<Annotation, Object> factory =
                                (AnnotationValidator<Annotation, Object>) constraint.validator().getDeclaredConstructor().newInstance();

                        validatorsForField.add(factory.create(annotation));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize validator for field: " + field.getName(), e);
                    }
                }
            }

            if (!validatorsForField.isEmpty()) {
                field.setAccessible(true);
                plan.add(new FieldValidator(field, validatorsForField));
            }
        }

        return plan;
    }

    private record FieldValidator(Field field, List<IValidator<Object>> validators) {
    }
}
