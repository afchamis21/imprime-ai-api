package org.imprime.ai.api.validator.annotations;

import lombok.Builder;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.IValidator;
import org.imprime.ai.api.validator.reflection.AnnotationValidator;
import org.imprime.ai.api.validator.reflection.Constraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validator = StringLength.Factory.class)
public @interface StringLength {
    int minLength() default 0;
    int maxLength();

    MessageCd minLengthMessage();
    MessageCd maxLengthMessage();

    final class Factory implements AnnotationValidator<StringLength, String> {

        @Override
        public IValidator<String> create(StringLength annotation) {
            return StringLengthValidator.builder()
                    .minLength(annotation.minLength())
                    .minLengthMessageCd(annotation.minLengthMessage())
                    .maxLength(annotation.maxLength())
                    .maxLengthMessageCd(annotation.maxLengthMessage())
                    .build();
        }
    }

    @Builder
    final class StringLengthValidator implements IValidator<String> {
        private final Integer minLength;
        private final Integer maxLength;

        private final MessageCd minLengthMessageCd;
        private final MessageCd maxLengthMessageCd;


        @Override
        public MessageCd validate(String value) {
            if (value == null) {
                return null;
            }

            int length = value.trim().length();

            if (length < minLength) {
                return minLengthMessageCd;
            }

            if (length > maxLength) {
                return maxLengthMessageCd;
            }

            return null;
        }
    }
}
