package org.imprime.ai.api.http.request.user;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import jakarta.annotation.Nullable;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;

import java.util.Set;
import java.util.regex.Pattern;

public record RegisterUserRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        String confirmPassword,
        DocumentType documentType,
        String document,
        String phoneNumber,
        RegisterAddressRequest address,
        @Nullable RegisterCompanyRequest company,
        Boolean isMaker
) implements ValidatedRequest {

    private static final Pattern FIRST_NAME_PATTERN =
            Pattern.compile("^[\\p{L}]+$");

    private static final Pattern LAST_NAME_PATTERN =
            Pattern.compile("^[\\p{L}\\s]+$");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$");

    @Nullable
    public MessageCd getInvalidReason() {
        if (firstName == null) {
            return MessageCd.MISSING_USER_FIRST_NAME;
        }

        if (firstName.length() < 3 || firstName.length() > 100) {
            return MessageCd.USER_FIRST_NAME_INVALID_LENGTH;
        }

        if (!FIRST_NAME_PATTERN.matcher(firstName).matches()) {
            return MessageCd.INVALID_USER_FIRST_NAME;
        }

        if (lastName == null) {
            return MessageCd.MISSING_USER_LAST_NAME;
        }

        if (lastName.length() < 3 || lastName.length() > 100) {
            return MessageCd.USER_LAST_NAME_INVALID_LENGTH;
        }

        if (!LAST_NAME_PATTERN.matcher(lastName).matches()) {
            return MessageCd.INVALID_USER_LAST_NAME;
        }

        if (email == null) {
            return MessageCd.MISSING_USER_EMAIL;
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return MessageCd.INVALID_USER_EMAIL;
        }

        if (password == null) {
            return MessageCd.MISSING_USER_PASSWORD;
        }

        if (password.length() < 8 || password.length() > 32 || !PASSWORD_PATTERN.matcher(password).matches()) {
            return MessageCd.INVALID_USER_PASSWORD;
        }

        if (confirmPassword == null) {
            return MessageCd.MISSING_USER_CONFIRM_PASSWORD;
        }

        if (!password.equals(confirmPassword)) {
            return MessageCd.USER_PASSWORD_MISMATCH;
        }

        if (documentType == null) {
            return MessageCd.MISSING_USER_DOCUMENT_TYPE;
        }

        if (document == null) {
            return MessageCd.MISSING_USER_DOCUMENT;
        }

        if (!Set.of(DocumentType.CPF, DocumentType.RG).contains(documentType)) {
            return MessageCd.INVALID_DOCUMENT_TYPE;
        }

        if (!documentType.validate(document)) {
            MessageCd message = switch (documentType) {
                case CPF -> MessageCd.INVALID_CPF;
                case RG -> MessageCd.INVALID_RG;
                case CNPJ -> null;
            };

            if (message != null) {
                return message;
            }
        }

        if (phoneNumber == null) {
            return MessageCd.MISSING_USER_PHONE_NUMBER;
        }

        if (!isValidPhoneNumber(phoneNumber)) {
            return MessageCd.INVALID_USER_PHONE_NUMBER;
        }

        if (address == null) {
            return MessageCd.MISSING_USER_ADDRESS;
        }

        address.validateOrThrow();

        if (Boolean.TRUE.equals(isMaker)) {
            if (company == null) {
                return MessageCd.MISSING_USER_COMPANY;
            }

            company.validateOrThrow();
        }

        return null;
    }

    private boolean isValidPhoneNumber(String value) {
        try {
            PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
            String clean = value.replaceAll("[^0-9]", "");
            var parsed = phoneNumberUtil.parse("+55" + clean, "BR");

            return phoneNumberUtil.isValidNumber(parsed);
        } catch (NumberParseException ex) {
            return false;
        }
    }
}