package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Resumo administrativo de modelo 3D na fila de moderação")
public record AdminModelSummaryResponse(
        String guid,
        String slug,
        String name,
        String category,
        BigDecimal price,
        FileFormat3D format,
        LicenseType license,
        boolean customizable,
        String coverImageUrl,
        String authorGuid,
        String authorName,
        String authorEmail,
        ModelModerationStatus moderationStatus,
        String moderationReason,
        String dmcaClaimant,
        String dmcaReference,
        Instant moderatedAt,
        StatusCd status,
        long downloadsCount,
        long viewsCount,
        Instant createdAt
) {
}
