package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.RefundType;

import java.math.BigDecimal;

@Schema(description = "Payload administrativo para criar solicitação/processamento de reembolso total ou parcial")
public record AdminCreateRefundRequest(
        @NotNull(message = "O tipo de reembolso (FULL ou PARTIAL) é obrigatório")
        RefundType refundType,
        BigDecimal amount,
        @NotBlank(message = "O motivo do reembolso é obrigatório")
        @Size(max = 2000)
        String reason,
        @Size(max = 2000)
        String adminNotes,
        boolean autoApprove
) {
}
