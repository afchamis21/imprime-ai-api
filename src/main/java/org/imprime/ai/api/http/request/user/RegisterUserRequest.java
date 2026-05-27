package org.imprime.ai.api.http.request.user;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;

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
    @Nullable
    public MessageCd getInvalidReason() {
        // TODO Implement this
        throw new RuntimeException("Unimplemented method getInvalidReason");
    }
}
