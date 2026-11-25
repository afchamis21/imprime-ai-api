package org.imprime.ai.api.http.request.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Atualiza as anotações técnicas privadas do vendedor vinculadas a uma negociação no chat")
public record UpdateChatSellerNotesRequest(
        @Schema(description = "Descrição / observações técnicas do Maker para este pedido (máximo 2000 caracteres)")
        @Size(max = 2000)
        String sellerNotes
) {
}
