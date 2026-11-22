package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload administrativo para recusa de solicitação de reembolso")
public record AdminRejectRefundRequest(
        @NotBlank(message = "O motivo da recusa é obrigatório")
        @Size(max = 2000)
        String reason
) {
}
