package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Payload administrativo para aprovação de reembolso (total ou parcial)")
public record AdminApproveRefundRequest(
        BigDecimal approvedAmount,
        @Size(max = 2000)
        String adminNotes
) {
}
