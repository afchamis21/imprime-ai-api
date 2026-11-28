package org.imprime.ai.api.service;

import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.SetupFeeScope;
import org.imprime.ai.api.service.MarketplaceQuoteCalculator.CalculatedOrderQuote;
import org.imprime.ai.api.service.MarketplaceQuoteCalculator.PlateQuoteInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceQuoteCalculatorTest {

    private final MarketplaceQuoteCalculator calculator = new MarketplaceQuoteCalculator();

    @Test
    void calculatesMaterialMachineSupportAndSetupPerPlateAccurately() {
        MakerPricingProfile profile = MakerPricingProfile.defaultForCompany(1L);
        profile.setHourlyRateBrl(new BigDecimal("6.00"));
        profile.setMinimumOrderFeeBrl(new BigDecimal("15.00"));
        profile.setSetupFeeBrl(new BigDecimal("4.00"));
        profile.setSetupFeeScope(SetupFeeScope.PER_PLATE);
        profile.setSupportRemovalFeeBrl(new BigDecimal("5.00"));
        profile.setSupportPricePerGramBrl(new BigDecimal("0.1000"));
        profile.setLeadTimeDays(3);

        PlateQuoteInput input = new PlateQuoteInput(
                1, "Suporte VESA", MaterialType.PETG, 2,
                new BigDecimal("60.00"), new BigDecimal("10.00"),
                3600, "raw", "normal",
                10L, "p-1", "A1", "Bambu Lab A1",
                PrinterSpeedTier.HIGH_SPEED, new BigDecimal("1.00")
        );

        // Per plate:
        // totalWeight = (60+10)*2 = 140g -> material = 140 * 0.25 = 35.00
        // time = 3600 * 1.0 * 2 = 7200s (2h) -> machine = 2 * 6.00 = 12.00
        // support > 0 -> 5.00*2 + 20*0.10 = 12.00
        // setup (PER_PLATE) = 4.00
        // total = 35 + 12 + 12 + 4 = 63.00
        CalculatedOrderQuote quote = calculator.calculate(
                profile,
                Map.of(MaterialType.PETG, new BigDecimal("0.2500")),
                List.of(input)
        );

        assertEquals(new BigDecimal("63.00"), quote.totalPriceBrl());
        assertFalse(quote.minimumOrderFeeApplied());
        assertEquals(new BigDecimal("35.00"), quote.materialCostBrl());
        assertEquals(new BigDecimal("12.00"), quote.machineCostBrl());
        assertEquals(new BigDecimal("12.00"), quote.supportCostBrl());
        assertEquals(new BigDecimal("4.00"), quote.setupFeeBrl());
        assertEquals(7200, quote.totalEstimatedPrintTimeSeconds());
        assertEquals(3, quote.estimatedLeadTimeDays()); // ceil(2/12)+1 = 2 < max(3,2)=3
    }

    @Test
    void doesNotApplySupportFeeWhenSupportWeightIsZeroAndAppliesMinimumFloor() {
        MakerPricingProfile profile = MakerPricingProfile.defaultForCompany(1L);
        profile.setHourlyRateBrl(new BigDecimal("4.00"));
        profile.setMinimumOrderFeeBrl(new BigDecimal("25.00"));
        profile.setSetupFeeBrl(new BigDecimal("3.00"));
        profile.setSetupFeeScope(SetupFeeScope.PER_ORDER);
        profile.setSupportRemovalFeeBrl(new BigDecimal("10.00"));
        profile.setSupportPricePerGramBrl(new BigDecimal("0.5000"));
        profile.setLeadTimeDays(2);

        PlateQuoteInput tinyPart = new PlateQuoteInput(
                1, "Chaveiro", MaterialType.PLA, 1,
                new BigDecimal("10.00"), BigDecimal.ZERO,
                1800, "raw", "normal",
                null, null, null, null,
                PrinterSpeedTier.STANDARD, new BigDecimal("2.20")
        );

        // estimatedTime = ceil(1800 * 2.2) = 3960s = 1.1h -> machine = 4.40
        // material = 10 * 0.20 = 2.00
        // support = 0.00 (no supportWeight)
        // PER_ORDER setup = 3.00 -> raw = 9.40 -> floor 25.00 -> delta 15.60
        CalculatedOrderQuote quote = calculator.calculate(
                profile,
                Map.of(MaterialType.PLA, new BigDecimal("0.2000")),
                List.of(tinyPart)
        );

        assertTrue(quote.minimumOrderFeeApplied());
        assertEquals(new BigDecimal("25.00"), quote.totalPriceBrl());
        assertEquals(new BigDecimal("15.60"), quote.minimumOrderDeltaBrl());
        assertEquals(new BigDecimal("0.00"), quote.supportCostBrl());
        assertEquals(new BigDecimal("3.00"), quote.setupFeeBrl());
        assertEquals(3960, quote.totalEstimatedPrintTimeSeconds());
    }

    @Test
    void appliesPerUnitSetupFinishExpressMultipliersAndExtendsLeadTimeForLongPrints() {
        MakerPricingProfile profile = MakerPricingProfile.defaultForCompany(1L);
        profile.setHourlyRateBrl(BigDecimal.ZERO);
        profile.setMinimumOrderFeeBrl(BigDecimal.ZERO);
        profile.setSetupFeeBrl(new BigDecimal("2.00"));
        profile.setSetupFeeScope(SetupFeeScope.PER_UNIT);
        profile.setFinishPaintedMultiplier(new BigDecimal("1.50"));
        profile.setExpressMultiplier(new BigDecimal("1.20"));
        profile.setLeadTimeDays(2);

        // 2 units * 36000s (10h each) * 1.4x = 100800s = 28h -> ceil(28/12)+1 = 4 days > 2
        PlateQuoteInput plate = new PlateQuoteInput(
                1, "Peça Grande", MaterialType.ABS, 2,
                new BigDecimal("50.00"), BigDecimal.ZERO,
                36000, "painted", "express",
                2L, "p-2", "MK4", "Prusa MK4",
                PrinterSpeedTier.MEDIUM_SPEED, new BigDecimal("1.40")
        );

        // material = 100 * 0.20 = 20.00, setup PER_UNIT = 2*2 = 4.00, base = 24.00
        // mult = 1.50 * 1.20 = 1.80 -> total = 43.20, adj = 19.20
        CalculatedOrderQuote quote = calculator.calculate(
                profile,
                Map.of(MaterialType.ABS, new BigDecimal("0.2000")),
                List.of(plate)
        );

        assertEquals(new BigDecimal("43.20"), quote.totalPriceBrl());
        assertEquals(new BigDecimal("19.20"), quote.finishAndUrgencyAdjustmentBrl());
        assertEquals(4, quote.estimatedLeadTimeDays());
    }
}
