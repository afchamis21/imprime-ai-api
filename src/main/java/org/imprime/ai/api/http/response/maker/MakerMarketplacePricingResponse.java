package org.imprime.ai.api.http.response.maker;

import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.SetupFeeScope;

import java.math.BigDecimal;
import java.util.List;

public record MakerMarketplacePricingResponse(
        boolean instantQuoteEnabled,
        BigDecimal hourlyRateBrl,
        BigDecimal minimumOrderFeeBrl,
        BigDecimal setupFeeBrl,
        SetupFeeScope setupFeeScope,
        BigDecimal supportRemovalFeeBrl,
        BigDecimal supportPricePerGramBrl,
        Integer leadTimeDays,
        BigDecimal expressMultiplier,
        BigDecimal finishSandedMultiplier,
        BigDecimal finishVaporMultiplier,
        BigDecimal finishPaintedMultiplier,
        List<MaterialPriceItem> materialPrices,
        List<PrinterSpeedPreview> printersPreview
) {
    public record MaterialPriceItem(
            MaterialType material,
            BigDecimal pricePerGramBrl,
            boolean enabled,
            String notes
    ) {
    }

    public record PrinterSpeedPreview(
            String printerGuid,
            String name,
            String model,
            String printerCatalogGuid,
            PrinterSpeedTier speedTier,
            BigDecimal speedMultiplier,
            String speedSource,
            BigDecimal buildVolumeXMm,
            BigDecimal buildVolumeYMm,
            BigDecimal buildVolumeZMm
    ) {
    }
}
