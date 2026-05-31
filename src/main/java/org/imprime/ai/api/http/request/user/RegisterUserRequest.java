package org.imprime.ai.api.http.request.user;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.annotations.*;
import org.imprime.ai.api.validator.reflection.ValidatorEngine;

@Slf4j
public record RegisterUserRequest(
        @Required(message = MessageCd.MISSING_USER_FIRST_NAME)
        @StringLength(minLength = 3, maxLength = 100, minLengthMessage = MessageCd.USER_FIRST_NAME_INVALID_LENGTH, maxLengthMessage =  MessageCd.USER_FIRST_NAME_INVALID_LENGTH)
        @Regex(regex = "^[\\p{L}]+$", message =  MessageCd.INVALID_USER_FIRST_NAME)
        String firstName,

        @Required(message = MessageCd.MISSING_USER_LAST_NAME)
        @StringLength(minLength = 3, maxLength = 100, minLengthMessage = MessageCd.USER_LAST_NAME_INVALID_LENGTH, maxLengthMessage =  MessageCd.USER_LAST_NAME_INVALID_LENGTH)
        @Regex(regex = "^[\\p{L}\\s]+$", message =  MessageCd.INVALID_USER_LAST_NAME)
        String lastName,

        @Required(message = MessageCd.MISSING_USER_EMAIL)
        @StringLength(maxLength = 255, minLengthMessage = MessageCd.INVALID_USER_EMAIL, maxLengthMessage =  MessageCd.INVALID_USER_EMAIL)
        @Regex(regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$",  message =  MessageCd.INVALID_USER_EMAIL)
        String email,

        @Required(message = MessageCd.MISSING_USER_PASSWORD)
        @StringLength(minLength = 8,maxLength = 32, minLengthMessage = MessageCd.INVALID_USER_PASSWORD, maxLengthMessage =  MessageCd.INVALID_USER_PASSWORD)
        @Regex(regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$",  message =  MessageCd.INVALID_USER_PASSWORD)
        String password,

        @Required(message = MessageCd.USER_PASSWORD_MISMATCH)
        String confirmPassword,

        @Required(message = MessageCd.MISSING_USER_DOCUMENT_TYPE)
        @DocumentTypes(types = {DocumentType.CPF, DocumentType.RG}, message = MessageCd.INVALID_DOCUMENT_TYPE)
        DocumentType documentType,

        @Required(message = MessageCd.MISSING_USER_DOCUMENT)
        String document,

        @Required(message = MessageCd.MISSING_USER_PHONE_NUMBER)
        @PhoneNumber(invalidPhoneNumberMessage = MessageCd.INVALID_USER_PHONE_NUMBER)
        String phoneNumber,

        @Required(message = MessageCd.MISSING_USER_ADDRESS)
        RegisterAddressRequest address,

        Boolean isMaker,
        @Nullable RegisterCompanyRequest company
) implements ValidatedRequest {
    @Nullable
    public MessageCd getInvalidReason() {
        try {
            MessageCd messageCd = ValidatorEngine.validate(this);
            if (messageCd != null) {
                return messageCd;
            }
        } catch (Exception e) {
            log.error("Error running reflective validations for User Request!", e);
        }

        if (!password.equals(confirmPassword)) {
            return MessageCd.USER_PASSWORD_MISMATCH;
        }

        MessageCd invalidDocumentMessage = documentType.validate(document);
        if (invalidDocumentMessage != null) {
            return invalidDocumentMessage;
        }

        if (Boolean.TRUE.equals(isMaker)) {
            if (company == null) {
                return MessageCd.MISSING_USER_COMPANY;
            }

            MessageCd messageCd = company.getInvalidReason();
            if (messageCd != null) {
                return messageCd;
            }
        }

        return address.getInvalidReason();
    }
}