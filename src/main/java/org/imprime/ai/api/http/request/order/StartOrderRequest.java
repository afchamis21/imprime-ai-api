package org.imprime.ai.api.http.request.order;

import org.imprime.ai.api.http.request.base.ValidatedRequest;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.validator.annotations.Required;

public record StartOrderRequest(
        @Required(message = MessageCd.MISSING_ADDRESS_GUID)
        String addressGuid
) implements ValidatedRequest {
}
