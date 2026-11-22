package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados opcionais para concluir um repasse (payout) manualmente")
public record AdminPayoutCompleteRequest(
        @Schema(description = "Código de comprovante ou referência bancária/PIX", example = "E12345678202611211630")
        @Size(max = 100, message = "A referência deve ter no máximo 100 caracteres")
        String reference,

        @Schema(description = "Observação opcional para registro em auditoria")
        @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres")
        String notes
) {
}
