package org.imprime.ai.api.service;

import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MakerEligibilityServiceTest {

    private MakerEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new MakerEligibilityService(new JsonCodec());
    }

    @Test
    void shouldRejectMakersNotAcceptingCustomQuotesOrInVacationModeOrNotApproved() {
        Company maker = baseCompany(1L, true, 4, true, List.of(MaterialType.PLA));
        Asset printer = basePrinter(1L, 10L, 256, 256, 256, true, 4);
        OrderFileAsset plate = plate("PLA", 100, 100, 100, false, 1);

        assertTrue(service.isEligible(maker, List.of(printer), Map.of(), List.of(plate)));

        maker.setAcceptingCustomQuotes(false);
        assertFalse(service.isEligible(maker, List.of(printer), Map.of(), List.of(plate)));

        maker.setAcceptingCustomQuotes(true);
        maker.setVacationMode(true);
        assertFalse(service.isEligible(maker, List.of(printer), Map.of(), List.of(plate)));

        maker.setVacationMode(false);
        maker.setApprovalStatus(MakerApprovalStatus.PENDING_APPROVAL);
        assertFalse(service.isEligible(maker, List.of(printer), Map.of(), List.of(plate)));
    }

    @Test
    void shouldFilterByMulticolorCapabilityAndSlotCount() {
        Company monoMaker = baseCompany(1L, true, 1, false, List.of(MaterialType.PLA));
        Asset monoPrinter = basePrinter(1L, null, 256, 256, 256, false, 1);

        Company multiMaker = baseCompany(2L, true, 4, true, List.of(MaterialType.PLA));
        Asset multiPrinter = basePrinter(2L, null, 256, 256, 256, true, 4);

        OrderFileAsset monoPlate = plate("PLA", 120, 120, 80, false, 1);
        OrderFileAsset colorPlate3 = plate("PLA", 120, 120, 80, true, 3);
        OrderFileAsset colorPlate6 = plate("PLA", 120, 120, 80, true, 6);

        assertTrue(service.isEligible(monoMaker, List.of(monoPrinter), Map.of(), List.of(monoPlate)));
        assertFalse(service.isEligible(monoMaker, List.of(monoPrinter), Map.of(), List.of(colorPlate3)));

        assertTrue(service.isEligible(multiMaker, List.of(multiPrinter), Map.of(), List.of(colorPlate3)));
        assertFalse(service.isEligible(multiMaker, List.of(multiPrinter), Map.of(), List.of(colorPlate6)));
    }

    @Test
    void shouldValidateBuildVolumeWithXy90DegreeRotationAndJointMulticolorConstraint() {
        Company maker = baseCompany(1L, true, 4, true, List.of(MaterialType.PLA));
        // Mixed fleet: big monochrome printer + compact multicolor printer
        Asset bigMono = basePrinter(1L, null, 350, 220, 300, false, 1);
        Asset smallMulti = basePrinter(1L, null, 180, 180, 180, true, 4);

        // Fits bigMono when rotated 90deg in XY (210 <= 220 and 310 <= 350)
        OrderFileAsset rotatedMono = plate("PLA", 210, 310, 250, false, 1);
        assertTrue(service.isEligible(maker, List.of(bigMono, smallMulti), Map.of(), List.of(rotatedMono)));

        // Too tall in Z
        OrderFileAsset tooTall = plate("PLA", 100, 100, 320, false, 1);
        assertFalse(service.isEligible(maker, List.of(bigMono, smallMulti), Map.of(), List.of(tooTall)));

        // Multicolor plate 200x200x100 cannot run on smallMulti (180x180) and bigMono lacks multicolor
        OrderFileAsset oversizedMulti = plate("PLA", 200, 200, 100, true, 2);
        assertFalse(service.isEligible(maker, List.of(bigMono, smallMulti), Map.of(), List.of(oversizedMulti)));

        // Compact multicolor plate 150x150x100 fits smallMulti
        OrderFileAsset compactMulti = plate("PLA", 150, 150, 100, true, 4);
        assertTrue(service.isEligible(maker, List.of(bigMono, smallMulti), Map.of(), List.of(compactMulti)));
    }

    @Test
    void supportsRequiredMaterialsIsImplementedEvenWhileDisabledInIsEligible() {
        PrinterCatalog plaOnlyCatalog = new PrinterCatalog();
        plaOnlyCatalog.setId(50L);
        plaOnlyCatalog.setSupportedMaterials(new LinkedHashSet<>(Set.of(MaterialType.PLA, MaterialType.PETG)));

        Company plaMaker = baseCompany(1L, true, 4, false, List.of(MaterialType.PLA, MaterialType.PETG));
        Asset a1 = basePrinter(1L, 50L, 256, 256, 256, false, 1);

        OrderFileAsset absPlate = plate("ABS", 100, 100, 100, false, 1);
        OrderFileAsset plaPlate = plate("PLA", 100, 100, 100, false, 1);

        // Direct unit tests on supportsRequiredMaterials(...)
        assertTrue(service.supportsRequiredMaterials(plaMaker, List.of(a1), Map.of(50L, plaOnlyCatalog), List.of(plaPlate)));
        assertFalse(service.supportsRequiredMaterials(plaMaker, List.of(a1), Map.of(50L, plaOnlyCatalog), List.of(absPlate)));

        // While material validation is intentionally commented out in isEligible(...), ABS still passes when size matches
        assertTrue(service.isEligible(plaMaker, List.of(a1), Map.of(50L, plaOnlyCatalog), List.of(absPlate)));

        // Adding ABS support in catalog makes supportsRequiredMaterials return true
        PrinterCatalog p1sCatalog = new PrinterCatalog();
        p1sCatalog.setId(51L);
        p1sCatalog.setSupportedMaterials(new LinkedHashSet<>(Set.of(MaterialType.PLA, MaterialType.ABS, MaterialType.ASA)));
        Asset p1s = basePrinter(1L, 51L, 256, 256, 256, false, 1);
        assertTrue(service.supportsRequiredMaterials(plaMaker, List.of(p1s), Map.of(51L, p1sCatalog), List.of(absPlate)));
    }

    private Company baseCompany(
            Long id,
            boolean acceptingCustomQuotes,
            int maxSlots,
            boolean multicolor,
            List<MaterialType> materials
    ) {
        Company c = new Company();
        c.setId(id);
        c.setStatus(StatusCd.ACTIVE);
        c.setApprovalStatus(MakerApprovalStatus.APPROVED);
        c.setVacationMode(false);
        c.setAcceptingCustomQuotes(acceptingCustomQuotes);
        c.setSupportsMulticolor(multicolor);
        c.setMaxMulticolorSlots(maxSlots);
        c.setMaterialsJson(new JsonCodec().toEnumArrayJson(materials));
        c.setCapabilitiesJson(multicolor ? "[\"FDM\",\"MULTICOLOR_AMS\"]" : "[\"FDM\"]");
        return c;
    }

    private Asset basePrinter(
            Long companyId,
            Long catalogId,
            double x,
            double y,
            double z,
            boolean multicolor,
            int slots
    ) {
        Asset a = new Asset();
        a.setCompanyId(companyId);
        a.setPrinterCatalogId(catalogId);
        a.setStatus(StatusCd.ACTIVE);
        a.setTrayWidth(BigDecimal.valueOf(x));
        a.setTrayDepth(BigDecimal.valueOf(y));
        a.setTrayHeight(BigDecimal.valueOf(z));
        a.setSupportsMulticolor(multicolor);
        a.setMulticolorSlots(slots);
        return a;
    }

    private OrderFileAsset plate(String material, double x, double y, double z, boolean multi, int colors) {
        OrderFileAsset p = new OrderFileAsset();
        p.setMaterial(material);
        p.setDimensionXMm(BigDecimal.valueOf(x));
        p.setDimensionYMm(BigDecimal.valueOf(y));
        p.setDimensionZMm(BigDecimal.valueOf(z));
        p.setIsMultiColor(multi);
        p.setColorCount(colors);
        return p;
    }

    @Test
    void matchPrintersForInstantQuoteSelectsFastestCompatiblePrinterWithXyRotationAndMaterials() {
        Company maker = baseCompany(10L, true, 4, true, List.of(MaterialType.PLA, MaterialType.PETG));

        Asset slowLarge = basePrinter(10L, null, 300, 300, 300, false, 1);
        slowLarge.setId(101L);
        slowLarge.setGuid("p-slow");
        slowLarge.setName("Ender 3 Max");
        slowLarge.setModel("Ender 3");
        slowLarge.setPrinterStatus(org.imprime.ai.api.model.enums.PrinterStatus.ONLINE);
        slowLarge.setSpeedTier(org.imprime.ai.api.model.enums.PrinterSpeedTier.STANDARD);

        Asset fastCompact = basePrinter(10L, null, 250, 200, 180, true, 4);
        fastCompact.setId(102L);
        fastCompact.setGuid("p-fast");
        fastCompact.setName("Bambu P1S");
        fastCompact.setModel("Bambu Lab P1S");
        fastCompact.setPrinterStatus(org.imprime.ai.api.model.enums.PrinterStatus.ONLINE);
        fastCompact.setSpeedTier(org.imprime.ai.api.model.enums.PrinterSpeedTier.HIGH_SPEED);

        Asset offlineFast = basePrinter(10L, null, 256, 256, 256, true, 4);
        offlineFast.setId(103L);
        offlineFast.setGuid("p-off");
        offlineFast.setPrinterStatus(org.imprime.ai.api.model.enums.PrinterStatus.OFFLINE);
        offlineFast.setSpeedTier(org.imprime.ai.api.model.enums.PrinterSpeedTier.HIGH_SPEED);
        offlineFast.setSpeedMultiplierOverride(new BigDecimal("0.80"));

        // Needs 190x240 (fits 250x200 ONLY after 90-degree XY rotation)
        MakerEligibilityService.PlateMatchSpec spec = new MakerEligibilityService.PlateMatchSpec(
                1, MaterialType.PETG,
                new BigDecimal("190"), new BigDecimal("240"), new BigDecimal("100"),
                false, 1
        );

        PrinterSpeedResolver speedResolver = new PrinterSpeedResolver();
        var match = service.matchPrintersForInstantQuote(
                maker,
                List.of(slowLarge, fastCompact, offlineFast),
                Map.of(),
                List.of(spec),
                Set.of(MaterialType.PLA, MaterialType.PETG),
                speedResolver
        );

        assertTrue(match.eligible());
        assertNotNull(match.primaryPrinter());
        assertEquals("p-fast", match.primaryPrinter().printerGuid());
        assertEquals(org.imprime.ai.api.model.enums.PrinterSpeedTier.HIGH_SPEED, match.primaryPrinter().speedTier());
        assertEquals(new BigDecimal("1.00"), match.primaryPrinter().speedMultiplier());

        // Missing PETG price -> ineligible
        var unpriced = service.matchPrintersForInstantQuote(
                maker, List.of(fastCompact), Map.of(), List.of(spec), Set.of(MaterialType.PLA), speedResolver
        );
        assertFalse(unpriced.eligible());
        assertEquals("MISSING_MATERIAL_PRICE", unpriced.rejectionReason());

        // Empty fleet -> legacy conservative STANDARD (256x256x256, 2.20x) fallback
        var legacyFallback = service.matchPrintersForInstantQuote(
                maker, List.of(), Map.of(), List.of(spec), Set.of(MaterialType.PETG), speedResolver
        );
        assertTrue(legacyFallback.eligible());
        assertEquals(org.imprime.ai.api.model.enums.PrinterSpeedTier.STANDARD, legacyFallback.primaryPrinter().speedTier());
        assertEquals(new BigDecimal("2.20"), legacyFallback.primaryPrinter().speedMultiplier());
    }
}
