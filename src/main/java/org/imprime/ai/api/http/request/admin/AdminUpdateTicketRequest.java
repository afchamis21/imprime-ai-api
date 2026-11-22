package org.imprime.ai.api.http.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.TicketPriority;

@Schema(description = "Atualização administrativa de status, prioridade, atribuição e notas de resolução de um ticket")
public record AdminUpdateTicketRequest(
        @Schema(description = "Novo status do ticket")
        SupportTicketStatus status,

        @Schema(description = "Nova prioridade do ticket")
        TicketPriority priority,

        @Schema(description = "GUID do administrador responsável (string vazia remove atribuição)")
        String assignedAdminGuid,

        @Schema(description = "Notas de resolução ou parecer interno da equipe")
        @Size(max = 4000, message = "As notas de resolução devem ter no máximo 4000 caracteres")
        String resolutionNotes,

        @Schema(description = "Motivo/observação registrada no log de auditoria")
        @Size(max = 1000, message = "O motivo deve ter no máximo 1000 caracteres")
        String reason
) {
}
