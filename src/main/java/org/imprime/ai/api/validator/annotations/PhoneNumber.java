package org.imprime.ai.api.validator.annotations;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
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
@Constraint(validator = PhoneNumber.Factory.class)
public @interface PhoneNumber {
    MessageCd invalidFormatMessage() default MessageCd.INVALID_PHONE_FORMAT;
    MessageCd invalidPhoneNumberMessage();

    final class Factory implements AnnotationValidator<PhoneNumber, String> {

        @Override
        public IValidator<String> create(PhoneNumber annotation) {
            return PhoneNumberValidator.builder()
                    .invalidFormatMessage(annotation.invalidFormatMessage())
                    .invalidPhoneNumberMessage(annotation.invalidPhoneNumberMessage())
                    .build();
        }
    }

    @Builder
    final class PhoneNumberValidator implements IValidator<String> {

        private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

        private final MessageCd invalidFormatMessage;
        private final MessageCd invalidPhoneNumberMessage;

        @Override
        public MessageCd validate(String value) {
            try {
                var parsed = PHONE_NUMBER_UTIL.parse(value, null);

                return PHONE_NUMBER_UTIL.isValidNumber(parsed)
                        ? null
                        : invalidPhoneNumberMessage;

            } catch (NumberParseException ex) {
                return invalidFormatMessage;
            }
        }
    }
}
