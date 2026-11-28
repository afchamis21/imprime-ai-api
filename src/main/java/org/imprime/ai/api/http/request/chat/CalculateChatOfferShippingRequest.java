package org.imprime.ai.api.http.request.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Solicitação de cálculo de frete para uma proposta específica no chat")
public record CalculateChatOfferShippingRequest(
        @Schema(description = "GUID opcional do endereço do comprador; quando omitido utiliza o endereço principal cadastrado", example = "123e4567-e89b-12d3-a456-426614174000")
        @Size(max = 64, message = "O GUID do endereço deve ter no máximo 64 caracteres")
        String addressGuid
) {
}
