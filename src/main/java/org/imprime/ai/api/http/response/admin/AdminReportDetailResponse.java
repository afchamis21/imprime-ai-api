package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.TicketPriority;

import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhamento completo da denúncia no painel administrativo")
public record AdminReportDetailResponse(
        String guid,
        String protocol,
        ReportTargetType targetType,
        String targetGuid,
        String targetSlug,
        String targetName,
        String targetImageUrl,
        String targetStatus,
        String targetOwnerGuid,
        String targetOwnerName,
        ReportReason reason,
        String description,
        List<String> evidenceUrls,
        ReportStatus reportStatus,
        TicketPriority priority,
        String reporterGuid,
        String reporterName,
        String reporterEmail,
        String assignedAdminGuid,
        String assignedAdminName,
        String reviewedByName,
        Instant reviewedAt,
        String resolutionNotes,
        long targetTotalReportsCount,
        long targetOpenReportsCount,
        Instant createdAt,
        Instant updatedAt
) {
}
