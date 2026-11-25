package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.TicketPriority;

@Schema(description = "Atualização administrativa de status, prioridade, atribuição e parecer de uma denúncia")
public record AdminUpdateReportRequest(
        @Schema(description = "Novo status da denúncia")
        ReportStatus reportStatus,

        @Schema(description = "Nova prioridade da denúncia")
        TicketPriority priority,

        @Schema(description = "GUID do administrador responsável (vazio ou UNASSIGNED remove atribuição)")
        String assignedAdminGuid,

        @Schema(description = "Notas de resolução ou parecer interno da moderação")
        @Size(max = 2000, message = "As notas de resolução devem ter no máximo 2000 caracteres")
        String resolutionNotes,

        @Schema(description = "Motivo/observação registrada no log de auditoria")
        @Size(max = 1000, message = "O motivo deve ter no máximo 1000 caracteres")
        String reason
) {
}
