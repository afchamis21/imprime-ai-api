package org.imprime.ai.api.http.request.company;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.annotations.DocumentTypes;
import org.imprime.ai.api.validator.annotations.Regex;
import org.imprime.ai.api.validator.annotations.Required;
import org.imprime.ai.api.validator.annotations.StringLength;
import org.imprime.ai.api.validator.reflection.ValidatorEngine;

@Slf4j
public record RegisterCompanyRequest(

        @Required(message = MessageCd.MISSING_COMPANY_NAME)
        @StringLength(minLength = 2, maxLength = 100, minLengthMessage = MessageCd.INVALID_COMPANY_NAME_LENGTH, maxLengthMessage = MessageCd.INVALID_COMPANY_NAME_LENGTH)
        @Regex(regex = "^[\\p{L}\\p{N}\\s'&.,()-]+$", message = MessageCd.INVALID_COMPANY_NAME)
        String name,

        @Required(message = MessageCd.MISSING_COMPANY_DOCUMENT_TYPE)
        @DocumentTypes(types = {DocumentType.CNPJ, DocumentType.CPF}, message = MessageCd.INVALID_COMPANY_DOCUMENT_TYPE)
        DocumentType documentType,

        @Required(message = MessageCd.MISSING_COMPANY_DOCUMENT)
        String document,

        @Required(message = MessageCd.MISSING_COMPANY_ADDRESS)
        RegisterAddressRequest address

) implements ValidatedRequest {

    @Nullable
    @Override
    public MessageCd getInvalidReason() {
        try {
            MessageCd messageCd = ValidatorEngine.validate(this);
            if (messageCd != null) {
                return messageCd;
            }
        } catch (Exception e) {
            log.error("Error running reflective validations for Company Request!", e);
        }

        MessageCd invalidDocumentMessage = documentType.validate(document);
        if (invalidDocumentMessage != null) {
            return invalidDocumentMessage;
        }

        // Nested validation
        return address.getInvalidReason();
    }
}