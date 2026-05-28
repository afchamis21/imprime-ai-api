package org.imprime.ai.api.http.request.company;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;

import java.util.Set;
import java.util.regex.Pattern;

public record RegisterCompanyRequest(
        String name,
        DocumentType documentType,
        String document,
        RegisterAddressRequest address
) implements ValidatedRequest {

    private static final Pattern COMPANY_NAME_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}\\s'&.,()-]+$");

    @Nullable
    public MessageCd getInvalidReason() {
        if (name == null) {
            return MessageCd.MISSING_COMPANY_NAME;
        }

        String normalizedName = name.trim();

        if (normalizedName.length() < 2 || normalizedName.length() > 100) {
            return MessageCd.INVALID_COMPANY_NAME_LENGTH;
        }

        if (!COMPANY_NAME_PATTERN.matcher(normalizedName).matches()) {
            return MessageCd.INVALID_COMPANY_NAME;
        }

        if (documentType == null) {
            return MessageCd.MISSING_COMPANY_DOCUMENT_TYPE;
        }

        if (document == null) {
            return MessageCd.MISSING_COMPANY_DOCUMENT;
        }

        if (!Set.of(DocumentType.CNPJ, DocumentType.CPF).contains(documentType)) {
            return MessageCd.INVALID_COMPANY_DOCUMENT_TYPE;
        }

        if (!documentType.validate(document)) {
            MessageCd message = switch (documentType) {
                case CPF -> null;
                case RG -> MessageCd.INVALID_RG;
                case CNPJ -> MessageCd.INVALID_CNPJ;
            };

            if (message != null) {
                return message;
            }        }

        if (address == null) {
            return MessageCd.MISSING_COMPANY_ADDRESS;
        }

        address.validateOrThrow();

        return null;
    }
}