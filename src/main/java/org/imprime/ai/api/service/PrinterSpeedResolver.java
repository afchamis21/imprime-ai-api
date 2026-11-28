package org.imprime.ai.api.service;

import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Resolves the effective speed tier and multiplier for a maker's printer asset or catalog entry
 * relative to the Bambu Lab A1 reference profile (1.00x).
 */
@Component
public class PrinterSpeedResolver {

    public record ResolvedPrinterSpeed(
            PrinterSpeedTier tier,
            BigDecimal multiplier,
            String source
    ) {
    }

    public ResolvedPrinterSpeed resolve(@Nullable Asset asset, @Nullable PrinterCatalog catalog) {
        if (asset != null && isPositive(asset.getSpeedMultiplierOverride())) {
            BigDecimal override = normalize(asset.getSpeedMultiplierOverride());
            PrinterSpeedTier tier = asset.getSpeedTier() != null
                    ? asset.getSpeedTier()
                    : PrinterSpeedTier.fromMultiplier(override);
            return new ResolvedPrinterSpeed(tier, override, "ASSET_OVERRIDE");
        }

        if (asset != null && asset.getSpeedTier() != null) {
            return new ResolvedPrinterSpeed(
                    asset.getSpeedTier(),
                    normalize(asset.getSpeedTier().getDefaultMultiplier()),
                    "ASSET_TIER"
            );
        }

        if (catalog != null) {
            return resolveCatalog(catalog);
        }

        return new ResolvedPrinterSpeed(
                PrinterSpeedTier.STANDARD,
                normalize(PrinterSpeedTier.STANDARD.getDefaultMultiplier()),
                "FALLBACK_STANDARD"
        );
    }

    public ResolvedPrinterSpeed resolveCatalog(@Nullable PrinterCatalog catalog) {
        if (catalog == null) {
            return new ResolvedPrinterSpeed(
                    PrinterSpeedTier.STANDARD,
                    normalize(PrinterSpeedTier.STANDARD.getDefaultMultiplier()),
                    "FALLBACK_STANDARD"
            );
        }

        PrinterSpeedTier catalogTier = catalog.getSpeedTier();
        if (isPositive(catalog.getSpeedMultiplier())) {
            BigDecimal multiplier = normalize(catalog.getSpeedMultiplier());
            PrinterSpeedTier effectiveTier = catalogTier != null
                    ? catalogTier
                    : PrinterSpeedTier.fromMultiplier(multiplier);
            return new ResolvedPrinterSpeed(effectiveTier, multiplier, "CATALOG_MULTIPLIER");
        }

        if (catalogTier != null) {
            return new ResolvedPrinterSpeed(
                    catalogTier,
                    normalize(catalogTier.getDefaultMultiplier()),
                    "CATALOG_TIER"
            );
        }

        return new ResolvedPrinterSpeed(
                PrinterSpeedTier.STANDARD,
                normalize(PrinterSpeedTier.STANDARD.getDefaultMultiplier()),
                "FALLBACK_STANDARD"
        );
    }

    private static boolean isPositive(@Nullable BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
