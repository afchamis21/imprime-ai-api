package org.imprime.ai.api.http.request.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MessageType;

import java.math.BigDecimal;

@Schema(description = "Dados de uma mensagem enviada em um chat")
public record SendMessageRequest(
        @Schema(description = "Conteúdo textual da mensagem ou valor da oferta", example = "Olá! Gostaria de cotar esta peça em PLA preto.", requiredMode = Schema.RequiredMode.REQUIRED)
        String content,
        @Schema(description = "Tipo da mensagem (`MESSAGE` para texto comum, `OFFER` para proposta de valor)", example = "MESSAGE", requiredMode = Schema.RequiredMode.REQUIRED)
        MessageType type,
        @Schema(description = "Prazo de produção em dias úteis (obrigatório em propostas enviadas pelo vendedor)", example = "5")
        Integer productionDays,
        @Schema(description = "Peso total estimado da embalagem em kg", example = "0.450")
        BigDecimal packageWeight,
        @Schema(description = "Altura da embalagem em cm", example = "12.00")
        BigDecimal packageHeight,
        @Schema(description = "Largura da embalagem em cm", example = "18.00")
        BigDecimal packageWidth,
        @Schema(description = "Comprimento da embalagem em cm", example = "22.00")
        BigDecimal packageLength
) {
    public SendMessageRequest(String content, MessageType type) {
        this(content, type, null, null, null, null, null);
    }
}
