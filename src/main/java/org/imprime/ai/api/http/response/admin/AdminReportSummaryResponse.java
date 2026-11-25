package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.TicketPriority;

import java.time.Instant;

@Schema(description = "Resumo de denúncia exibido na fila administrativa")
public record AdminReportSummaryResponse(
        String guid,
        String protocol,
        ReportTargetType targetType,
        String targetGuid,
        String targetSlug,
        String targetName,
        String targetImageUrl,
        String targetOwnerName,
        ReportReason reason,
        String descriptionPreview,
        ReportStatus reportStatus,
        TicketPriority priority,
        String reporterGuid,
        String reporterName,
        String reporterEmail,
        String assignedAdminGuid,
        String assignedAdminName,
        long targetTotalReportsCount,
        Instant createdAt,
        Instant updatedAt
) {
}
