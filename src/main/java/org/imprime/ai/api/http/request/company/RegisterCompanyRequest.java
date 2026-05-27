package org.imprime.ai.api.http.request.company;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;

public record RegisterCompanyRequest(
        String name,
        DocumentType documentType,
        String document,
        RegisterAddressRequest address
) implements ValidatedRequest {
    @Nullable
    public MessageCd getInvalidReason() {
        // TODO Implement this
        throw new RuntimeException("Unimplemented method getInvalidReason");
    }
}
