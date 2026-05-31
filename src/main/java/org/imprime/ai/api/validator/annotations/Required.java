package org.imprime.ai.api.validator.annotations;

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
@Constraint(validator = Required.Factory.class)
public @interface Required {
    MessageCd message();

    final class Factory implements AnnotationValidator<Required, Object> {

        @Override
        public IValidator<Object> create(Required annotation) {
            return new RequiredValidator(annotation.message());
        }
    }

    final class RequiredValidator implements IValidator<Object> {

        private final MessageCd messageCd;

        public RequiredValidator(MessageCd messageCd) {
            this.messageCd = messageCd;
        }

        @Override
        public MessageCd validate(Object value) {
            return value == null
                    ? messageCd
                    : null;
        }
    }
}
