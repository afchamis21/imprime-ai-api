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
import java.util.regex.Pattern;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validator = Regex.Factory.class)
public @interface Regex {
    String regex();
    MessageCd message();

    final class Factory implements AnnotationValidator<Regex, String> {

        @Override
        public IValidator<String> create(Regex annotation) {
            return RegexValidator.builder()
                    .regex(Pattern.compile(annotation.regex()))
                    .message(annotation.message())
                    .build();
        }
    }

    @Builder
    final class RegexValidator implements IValidator<String> {
        private final Pattern regex;
        private final MessageCd message;

        @Override
        public MessageCd validate(String value) {
            if (value == null) {
                return null;
            }

            if (!regex.matcher(value).matches()) {
                return message;
            }

            return null;
        }
    }
}
