package org.imprime.ai.api.http.response.report;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;

import java.time.Instant;

@Schema(description = "Resumo da denúncia registrada pelo usuário")
public record ReportResponse(
        String guid,
        String protocol,
        ReportTargetType targetType,
        String targetGuid,
        String targetSlug,
        String targetName,
        ReportReason reason,
        String description,
        ReportStatus reportStatus,
        Instant createdAt
) {
}
