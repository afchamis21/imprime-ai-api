package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Motivo obrigatório para reprovar ou registrar falha em um repasse")
public record AdminPayoutFailRequest(
        @Schema(description = "Motivo da falha/reprovação do payout", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O motivo da falha é obrigatório")
        @Size(max = 1000, message = "O motivo deve ter no máximo 1000 caracteres")
        String reason
) {
}
