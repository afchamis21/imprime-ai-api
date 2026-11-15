package org.imprime.ai.api.http.request.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Requisição para abertura de chat de cotação entre cliente e Maker")
public record StartChatRequest(
        @Schema(description = "Primeira mensagem a ser enviada na abertura do chat", requiredMode = Schema.RequiredMode.REQUIRED)
        SendMessageRequest message,
        @Schema(description = "GUID da empresa (Maker) destinatária", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        String makerGuid,
        @Schema(description = "GUID do pedido em status `QUOTING`", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7", requiredMode = Schema.RequiredMode.REQUIRED)
        String orderGuid
) {
}
