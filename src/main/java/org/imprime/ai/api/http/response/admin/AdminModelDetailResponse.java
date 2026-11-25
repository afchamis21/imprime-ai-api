package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.model3d.ScadParameterDefinition;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhes completos de modelo 3D para revisão administrativa e moderação DMCA")
public record AdminModelDetailResponse(
        String guid,
        String slug,
        String name,
        String category,
        BigDecimal price,
        String description,
        FileFormat3D format,
        LicenseType license,
        MaterialType recommendedMaterial,
        Integer estimatedWeightGrams,
        BigDecimal widthMm,
        BigDecimal depthMm,
        BigDecimal heightMm,
        Long fileSizeBytes,
        boolean customizable,
        String modelFileUrl,
        String coverImageUrl,
        List<String> imageUrls,
        List<String> tags,
        BigDecimal ratingAvg,
        int reviewsCount,
        long downloadsCount,
        long viewsCount,
        String authorGuid,
        String authorName,
        String authorEmail,
        ModelModerationStatus moderationStatus,
        String moderationReason,
        String dmcaClaimant,
        String dmcaReference,
        String moderatedByName,
        Instant moderatedAt,
        StatusCd status,
        Instant createdAt,
        Instant updatedAt,
        String scadCode,
        List<ScadParameterDefinition> scadParameters
) {
    public AdminModelDetailResponse(
            String guid,
            String slug,
            String name,
            String category,
            BigDecimal price,
            String description,
            FileFormat3D format,
            LicenseType license,
            MaterialType recommendedMaterial,
            Integer estimatedWeightGrams,
            BigDecimal widthMm,
            BigDecimal depthMm,
            BigDecimal heightMm,
            Long fileSizeBytes,
            boolean customizable,
            String modelFileUrl,
            String coverImageUrl,
            List<String> imageUrls,
            List<String> tags,
            BigDecimal ratingAvg,
            int reviewsCount,
            long downloadsCount,
            long viewsCount,
            String authorGuid,
            String authorName,
            String authorEmail,
            ModelModerationStatus moderationStatus,
            String moderationReason,
            String dmcaClaimant,
            String dmcaReference,
            String moderatedByName,
            Instant moderatedAt,
            StatusCd status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                guid,
                slug,
                name,
                category,
                price,
                description,
                format,
                license,
                recommendedMaterial,
                estimatedWeightGrams,
                widthMm,
                depthMm,
                heightMm,
                fileSizeBytes,
                customizable,
                modelFileUrl,
                coverImageUrl,
                imageUrls,
                tags,
                ratingAvg,
                reviewsCount,
                downloadsCount,
                viewsCount,
                authorGuid,
                authorName,
                authorEmail,
                moderationStatus,
                moderationReason,
                dmcaClaimant,
                dmcaReference,
                moderatedByName,
                moderatedAt,
                status,
                createdAt,
                updatedAt,
                null,
                List.of()
        );
    }
}
