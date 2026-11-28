package org.imprime.ai.api.http.request.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Payload para envio de proposta comercial ou contra-proposta no chat")
public record CreateChatOfferRequest(
        @Schema(description = "Valor do serviço de impressão 3D em BRL", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor do serviço é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor do serviço deve ser maior que zero")
        BigDecimal serviceAmount,

        @Schema(description = "Prazo de produção em dias úteis", example = "5")
        Integer productionDays,

        @Schema(description = "Peso total estimado da embalagem em kg", example = "0.450")
        BigDecimal packageWeight,

        @Schema(description = "Altura da embalagem em cm", example = "12.00")
        BigDecimal packageHeight,

        @Schema(description = "Largura da embalagem em cm", example = "18.00")
        BigDecimal packageWidth,

        @Schema(description = "Comprimento da embalagem em cm", example = "22.00")
        BigDecimal packageLength,

        @Schema(description = "Observação opcional enviada logo após a proposta", example = "Peça em PETG com acabamento lixado")
        @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres")
        String note
) {
}
