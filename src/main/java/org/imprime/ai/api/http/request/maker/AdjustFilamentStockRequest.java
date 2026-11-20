package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.StockAdjustmentReason;

import java.math.BigDecimal;

@Schema(description = "Movimentação auditável de ajuste de saldo de filamento/resina")
public record AdjustFilamentStockRequest(
        @Schema(description = "Variação em gramas (positiva para entrada, negativa para consumo)", example = "-50.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A variação em gramas é obrigatória")
        BigDecimal deltaGrams,

        @Schema(description = "Motivo operacional do ajuste", example = "CALIBRATION", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O motivo do ajuste é obrigatório")
        StockAdjustmentReason reason,

        @Schema(description = "Observações adicionais sobre a movimentação")
        @Size(max = 300, message = "As observações devem ter no máximo 300 caracteres")
        String notes
) {
}
