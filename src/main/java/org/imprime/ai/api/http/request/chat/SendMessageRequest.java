package org.imprime.ai.api.http.request.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MessageType;

@Schema(description = "Dados de uma mensagem enviada em um chat")
public record SendMessageRequest(
        @Schema(description = "Conteúdo textual da mensagem ou valor da oferta", example = "Olá! Gostaria de cotar esta peça em PLA preto.", requiredMode = Schema.RequiredMode.REQUIRED)
        String content,
        @Schema(description = "Tipo da mensagem (`MESSAGE` para texto comum, `OFFER` para proposta de valor)", example = "MESSAGE", requiredMode = Schema.RequiredMode.REQUIRED)
        MessageType type
) {
}