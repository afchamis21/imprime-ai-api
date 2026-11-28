package org.imprime.ai.api.slicing;

public record SlicingValidationQueueMessage(
        String orderGuid,
        long triggeredByUserId,
        int attempt
) {
}
