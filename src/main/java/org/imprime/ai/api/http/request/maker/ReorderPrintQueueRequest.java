package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Reordenação atômica e idempotente da fila de impressão")
public record ReorderPrintQueueRequest(
        @Schema(description = "Lista ordenada com os GUIDs dos jobs na nova sequência desejada", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "A lista ordenada de GUIDs de jobs é obrigatória")
        List<@NotBlank String> orderedJobGuids
) {
}
