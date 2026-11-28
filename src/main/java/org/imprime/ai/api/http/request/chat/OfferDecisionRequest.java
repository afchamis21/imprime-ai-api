package org.imprime.ai.api.http.request.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Decisão de aceite ou recusa sobre uma oferta de orçamento enviada no chat")
public record OfferDecisionRequest(
        @Schema(description = "Indica se a oferta foi aceita (`true`) ou recusada (`false`)", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A decisão de aceite é obrigatória")
        Boolean accept,

        @Schema(description = "Motivo ou comentário opcional da decisão", example = "Prazo e valor aprovados")
        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
        String reason,

        @Schema(description = "GUID da cotação de frete selecionada no Melhor Envio (quando o comprador aceita proposta com dados de envio)")
        @Size(max = 64)
        String shippingQuoteGuid,

        @Schema(description = "GUID do endereço de entrega escolhido pelo comprador")
        @Size(max = 64)
        String addressGuid
) {
    public OfferDecisionRequest(Boolean accept, String reason) {
        this(accept, reason, null, null);
    }
}
