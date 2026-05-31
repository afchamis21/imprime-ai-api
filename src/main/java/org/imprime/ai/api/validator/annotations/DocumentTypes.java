package org.imprime.ai.api.validator.annotations;

import lombok.Builder;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.IValidator;
import org.imprime.ai.api.validator.reflection.AnnotationValidator;
import org.imprime.ai.api.validator.reflection.Constraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validator = DocumentTypes.Factory.class)
public @interface DocumentTypes {
    DocumentType[] types();
    MessageCd message();

    final class Factory implements AnnotationValidator<DocumentTypes, DocumentType> {

        @Override
        public IValidator<DocumentType> create(DocumentTypes annotation) {
            return DocumentTypeValidator.builder()
                    .types(annotation.types())
                    .message(annotation.message())
                    .build();
        }
    }

    @Builder
    final class DocumentTypeValidator implements IValidator<DocumentType> {
        private final org.imprime.ai.api.model.enums.DocumentType[] types;
        private final MessageCd message;

        @Override
        public MessageCd validate(org.imprime.ai.api.model.enums.DocumentType value) {
            if (value == null) {
                return null;
            }

            if (!Set.of(types).contains(value)) {
                return message;
            }

            return null;
        }
    }
}
