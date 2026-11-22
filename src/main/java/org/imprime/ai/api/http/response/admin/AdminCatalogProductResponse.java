package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Produto ou serviço listado no catálogo administrativo")
public record AdminCatalogProductResponse(
        String guid,
        String slug,
        String name,
        String category,
        BigDecimal price,
        BigDecimal oldPrice,
        String material,
        String colorHex,
        String coverImageUrl,
        boolean inStock,
        int productionDays,
        int salesCount,
        BigDecimal ratingAvg,
        int reviewsCount,
        StatusCd status,
        String makerGuid,
        String makerName,
        MakerApprovalStatus makerApprovalStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
