package org.imprime.ai.api.http.request.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.OrderStatusCd;

@Schema(description = "Dados para transição de status de um pedido")
public record UpdateOrderStatusRequest(
        @Schema(description = "Novo status alvo do pedido", example = "IN_TRANSIT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O status alvo é obrigatório")
        OrderStatusCd status,

        @Schema(description = "Justificativa ou motivo da transição (obrigatório em cancelamento ou reembolso)", example = "Solicitado pelo cliente antes da produção")
        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
        String reason,

        @Schema(description = "Código de rastreio (opcional ao mover para IN_TRANSIT)", example = "BR123456789BR")
        @Size(max = 100, message = "O código de rastreio deve ter no máximo 100 caracteres")
        String trackingCode
) {
}
