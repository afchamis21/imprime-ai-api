package org.imprime.ai.api.http.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta simples contendo mensagem de confirmação da operação")
public record MessageStatusResponse(
        @Schema(description = "Mensagem descritiva do resultado da operação")
        String message
) {
}
