package org.imprime.ai.api.http.response.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.ExtruderType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterConnectivity;
import org.imprime.ai.api.model.enums.PrinterKinematics;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Especificação técnica completa de impressora 3D do catálogo fixo")
public record PrinterCatalogResponse(
        String guid,
        String slug,
        String brand,
        String modelName,
        String displayName,
        BigDecimal buildVolumeXMm,
        BigDecimal buildVolumeYMm,
        BigDecimal buildVolumeZMm,
        boolean isEnclosed,
        Integer maxNozzleTempC,
        Integer maxBedTempC,
        BigDecimal defaultNozzleMm,
        BigDecimal filamentDiameterMm,
        Integer maxPrintSpeedMmS,
        Integer maxPowerWatts110v,
        Integer maxPowerWatts220v,
        String inputVoltage,
        List<PrinterConnectivity> connectivityMethods,
        BigDecimal weightKg,
        PrinterTechnology technology,
        PrinterKinematics kinematics,
        ExtruderType extruderType,
        boolean supportsMulticolor,
        String multicolorSystemName,
        int maxColorSlots,
        Integer avgPowerWatts,
        boolean activeChamberHeating,
        Integer maxChamberTempC,
        BigDecimal minLayerHeightMm,
        BigDecimal maxLayerHeightMm,
        Integer maxAccelerationMmS2,
        List<BigDecimal> supportedNozzleSizesMm,
        boolean hardenedNozzleDefault,
        boolean hasAutoBedLeveling,
        boolean hasFilamentRunoutSensor,
        boolean hasCamera,
        boolean hasAirFiltration,
        Integer releaseYear,
        BigDecimal estimatedPriceBrl,
        String imageUrl,
        String officialSpecsUrl,
        String notes,
        List<MaterialType> supportedMaterials,
        long activeMakersFleetCount,
        PrinterSpeedTier speedTier,
        BigDecimal speedMultiplier,
        StatusCd status,
        Instant createdAt,
        Instant updatedAt
) {
}
