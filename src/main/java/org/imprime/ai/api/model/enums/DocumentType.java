package org.imprime.ai.api.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;
import org.imprime.ai.api.validator.CnpjValidator;
import org.imprime.ai.api.validator.CpfValidator;
import org.imprime.ai.api.validator.IValidator;
import org.imprime.ai.api.validator.RgValidator;

@Getter
@RequiredArgsConstructor
public enum DocumentType implements CodeAttribute {
    RG("RG", new RgValidator()),
    CPF("CPF", new CpfValidator()),
    CNPJ("CNPJ", new CnpjValidator());

    private final String code;
    private final IValidator<String> validator;

    public static class Converter extends CodeAttributeConverter<DocumentType> {
        protected Converter() {
            super(DocumentType.class);
        }
    }

    public MessageCd validate(String value) {
        return validator.validate(value);
    }
}
