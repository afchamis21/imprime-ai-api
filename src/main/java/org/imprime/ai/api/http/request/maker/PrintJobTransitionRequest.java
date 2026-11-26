package org.imprime.ai.api.http.request.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.PrintJobAction;

@Schema(description = "Transição de estado operacional de um job na fila de impressão")
public record PrintJobTransitionRequest(
        @Schema(description = "Ação de transição a aplicar no job", example = "COMPLETE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A ação de transição é obrigatória")
        PrintJobAction action,

        @Schema(description = "GUID da impressora (opcional ao iniciar/retomar para trocar a máquina alvo)")
        String printerGuid,

        @Schema(description = "Motivo da falha (quando action = FAIL)")
        @Size(max = 500, message = "O motivo da falha deve ter no máximo 500 caracteres")
        String failureReason, Boolean complianceApproved, String policyVersion
) {
    public PrintJobTransitionRequest(PrintJobAction action, String printerGuid, String failureReason) {
        this(action, printerGuid, failureReason, null, null);
    }
}
