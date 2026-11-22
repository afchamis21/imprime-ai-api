package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload administrativo para cancelamento de pedido")
public record AdminCancelOrderRequest(
        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        @Size(max = 500)
        String reason,
        boolean initiateFullRefund
) {
}
