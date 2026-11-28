package org.imprime.ai.api.service;

import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrinterSpeedResolverTest {

    private final PrinterSpeedResolver resolver = new PrinterSpeedResolver();

    @Test
    void resolvesAccordingToFullPrecedenceOrder() {
        PrinterCatalog catalog = new PrinterCatalog();
        catalog.setSpeedTier(PrinterSpeedTier.HIGH_SPEED);
        catalog.setSpeedMultiplier(new BigDecimal("1.00"));

        // Fallback STANDARD when both asset and catalog are absent
        PrinterSpeedResolver.ResolvedPrinterSpeed fallback = resolver.resolve(null, null);
        assertEquals(PrinterSpeedTier.STANDARD, fallback.tier());
        assertEquals(new BigDecimal("2.20"), fallback.multiplier());
        assertEquals("FALLBACK_STANDARD", fallback.source());

        // Catalog speed tier/multiplier used when asset has no override
        Asset asset = new Asset();
        PrinterSpeedResolver.ResolvedPrinterSpeed fromCatalog = resolver.resolve(asset, catalog);
        assertEquals(PrinterSpeedTier.HIGH_SPEED, fromCatalog.tier());
        assertEquals(new BigDecimal("1.00"), fromCatalog.multiplier());
        assertEquals("CATALOG_MULTIPLIER", fromCatalog.source());

        // Asset speedTier overrides catalog
        asset.setSpeedTier(PrinterSpeedTier.MEDIUM_SPEED);
        PrinterSpeedResolver.ResolvedPrinterSpeed fromAssetTier = resolver.resolve(asset, catalog);
        assertEquals(PrinterSpeedTier.MEDIUM_SPEED, fromAssetTier.tier());
        assertEquals(new BigDecimal("1.40"), fromAssetTier.multiplier());
        assertEquals("ASSET_TIER", fromAssetTier.source());

        // Explicit multiplier override has highest precedence
        asset.setSpeedMultiplierOverride(new BigDecimal("0.85"));
        PrinterSpeedResolver.ResolvedPrinterSpeed fromOverride = resolver.resolve(asset, catalog);
        assertEquals(PrinterSpeedTier.MEDIUM_SPEED, fromOverride.tier());
        assertEquals(new BigDecimal("0.85"), fromOverride.multiplier());
        assertEquals("ASSET_OVERRIDE", fromOverride.source());

        // Override without explicit asset tier infers tier from multiplier
        asset.setSpeedTier(null);
        asset.setSpeedMultiplierOverride(new BigDecimal("2.10"));
        PrinterSpeedResolver.ResolvedPrinterSpeed inferred = resolver.resolve(asset, catalog);
        assertEquals(PrinterSpeedTier.STANDARD, inferred.tier());
        assertEquals(new BigDecimal("2.10"), inferred.multiplier());
    }
}
