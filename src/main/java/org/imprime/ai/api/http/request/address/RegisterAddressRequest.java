package org.imprime.ai.api.http.request.address;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.MessageCd;

public record RegisterAddressRequest(
        String zipCode,
        String city,
        String state,
        String country,
        String addressLine1,
        @Nullable String addressLine2
) implements ValidatedRequest {
    @Nullable
    public MessageCd getInvalidReason() {
        // TODO Implement this
        throw new RuntimeException("Unimplemented method getInvalidReason");
    }
}
