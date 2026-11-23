package org.imprime.ai.api.http.request.maker;

import jakarta.validation.constraints.NotNull;
import org.imprime.ai.api.model.enums.QuoteHistoryStatus;

public record UpdateMakerQuoteStatusRequest(
        @NotNull(message = "Novo status do orçamento é obrigatório")
        QuoteHistoryStatus quoteStatus
) {
}
