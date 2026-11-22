package org.imprime.ai.api.http.response.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.http.response.model3d.ModelAuthorResponse;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ModelModerationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Modelo 3D publicado ou submetido pelo perfil do Maker")
public record MakerModelResponse(
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
        String coverImageUrl,
        List<String> imageUrls,
        List<String> tags,
        BigDecimal rating,
        int reviewsCount,
        long downloadsCount,
        long viewsCount,
        ModelModerationStatus moderationStatus,
        String moderationReason,
        Instant moderatedAt,
        boolean favorited,
        ModelAuthorResponse author,
        Instant createdAt
) {
}
