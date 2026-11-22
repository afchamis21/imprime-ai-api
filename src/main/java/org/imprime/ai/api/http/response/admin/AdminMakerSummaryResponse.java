package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Resumo administrativo de Maker e suas capacidades produtivas")
public record AdminMakerSummaryResponse(
        String guid,
        String slug,
        String name,
        String specialty,
        String specialtyCategory,
        String primaryPrinter,
        String city,
        String state,
        String ownerGuid,
        String ownerName,
        String ownerEmail,
        MakerApprovalStatus approvalStatus,
        String approvalReason,
        boolean verified,
        boolean online,
        boolean vacationMode,
        StatusCd status,
        int printersCount,
        int filamentsCount,
        int productsCount,
        BigDecimal ratingAvg,
        int printsDone,
        List<MaterialType> materials,
        List<MakerCapability> capabilities,
        Instant createdAt
) {
}
