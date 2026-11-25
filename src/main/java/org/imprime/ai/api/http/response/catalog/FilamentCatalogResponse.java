package org.imprime.ai.api.http.response.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.FilamentFinishType;
import org.imprime.ai.api.model.enums.HygroscopicLevel;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.SpoolType;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Perfil técnico de filamento do catálogo global")
public record FilamentCatalogResponse(
        String guid,
        String slug,
        String brand,
        String lineName,
        MaterialType material,
        String materialSubtype,
        String colorName,
        String colorHex,
        String secondaryColorHex,
        FilamentFinishType finishType,
        BigDecimal diameterMm,
        BigDecimal densityGCm3,
        Integer defaultSpoolWeightG,
        SpoolType spoolType,
        Integer nozzleTempMinC,
        Integer nozzleTempMaxC,
        Integer nozzleTempRecommendedC,
        Integer bedTempMinC,
        Integer bedTempMaxC,
        Integer bedTempRecommendedC,
        BigDecimal maxVolumetricSpeedMm3S,
        BigDecimal pressureAdvanceK,
        BigDecimal transmissionDistanceMm,
        Integer dryingTempC,
        Integer dryingTimeHours,
        boolean requiresEnclosure,
        boolean requiresHardenedNozzle,
        boolean amsCompatible,
        HygroscopicLevel hygroscopicLevel,
        String shoreHardness,
        BigDecimal referencePriceBrlPerKg,
        String sku,
        String gtinEan,
        String purchaseUrl,
        String imageUrl,
        String notes,
        long activeInventoryCount,
        StatusCd status,
        Instant createdAt,
        Instant updatedAt
) {
}
