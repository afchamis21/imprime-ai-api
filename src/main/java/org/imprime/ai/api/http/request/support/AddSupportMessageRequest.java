package org.imprime.ai.api.http.request.support;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Mensagem adicional em um chamado de suporte existente")
public record AddSupportMessageRequest(
        @Schema(description = "Texto da mensagem", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A mensagem é obrigatória")
        @Size(max = 4000, message = "A mensagem deve ter no máximo 4000 caracteres")
        String message
) {
}
