package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Motivo obrigatório para recusa ou reprovação administrativa")
public record AdminModerationReasonRequest(
        @NotBlank(message = "O motivo é obrigatório")
        @Size(max = 2000)
        String reason
) {
}
