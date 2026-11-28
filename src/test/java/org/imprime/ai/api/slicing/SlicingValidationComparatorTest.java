package org.imprime.ai.api.slicing;

import org.imprime.ai.api.config.SlicingValidationProperties;
import org.imprime.ai.api.config.SlicingValidationProperties.DivergencePolicy;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;
import org.imprime.ai.api.slicing.SlicingValidationComparator.OrderComparisonSummary;
import org.imprime.ai.api.slicing.SlicingValidationComparator.PlateComparisonInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlicingValidationComparatorTest {

    private final SlicingValidationComparator comparator = new SlicingValidationComparator();

    private SlicingValidationProperties.Validation defaultConfig() {
        SlicingValidationProperties.Validation cfg = new SlicingValidationProperties.Validation();
        cfg.setWeightTolerancePercent(5.0);
        cfg.setMinWeightToleranceGrams(2.0);
        cfg.setTimeTolerancePercent(10.0);
        cfg.setMinTimeToleranceSeconds(120);
        cfg.setHardRejectTolerancePercent(30.0);
        cfg.setDivergencePolicy(DivergencePolicy.RECALCULATE);
        return cfg;
    }

    @Test
    void acceptsWithinPercentageAndFloorTolerances() {
        OrderComparisonSummary summary = comparator.compare(
                defaultConfig(),
                List.of(
                        // Large part: 3% weight div (<5%), 7% time div (<10%) -> VALIDATED
                        new PlateComparisonInput(1, new BigDecimal("97.00"), 5580, new BigDecimal("100.00"), 6000),
                        // Tiny part: 25% weight div (delta 1.0g <= 2.0g floor), 15% time div (delta 60s <= 120s floor) -> VALIDATED
                        new PlateComparisonInput(2, new BigDecimal("3.00"), 340, new BigDecimal("4.00"), 400)
                )
        );

        assertEquals(SlicingValidationStatus.VALIDATED, summary.overallStatus());
        assertEquals(2, summary.plates().size());
        assertTrue(summary.plates().get(0).weightWithinTolerance());
        assertTrue(summary.plates().get(1).timeWithinTolerance());
    }

    @Test
    void returnsRecalculatedWhenBetweenToleranceAndHardRejectThreshold() {
        OrderComparisonSummary summary = comparator.compare(
                defaultConfig(),
                List.of(
                        // Client 85g vs Server 100g -> 15% (>5%, <=30%)
                        new PlateComparisonInput(1, new BigDecimal("85.00"), 3800, new BigDecimal("100.00"), 4000)
                )
        );

        assertEquals(SlicingValidationStatus.RECALCULATED, summary.overallStatus());
        assertFalse(summary.plates().getFirst().hardRejected());
        assertEquals(new BigDecimal("15.00"), summary.maxWeightDivergencePercent());
    }

    @Test
    void rejectsWhenAboveHardRejectThresholdOrWhenPolicyIsReject() {
        OrderComparisonSummary extremeTamper = comparator.compare(
                defaultConfig(),
                List.of(
                        // Client 50g vs Server 100g -> 50% (>30% hard reject)
                        new PlateComparisonInput(1, new BigDecimal("50.00"), 2000, new BigDecimal("100.00"), 4000)
                )
        );

        assertEquals(SlicingValidationStatus.REJECTED, extremeTamper.overallStatus());
        assertTrue(extremeTamper.plates().getFirst().hardRejected());

        SlicingValidationProperties.Validation strictReject = defaultConfig();
        strictReject.setDivergencePolicy(DivergencePolicy.REJECT);
        OrderComparisonSummary moderateUnderRejectPolicy = comparator.compare(
                strictReject,
                List.of(
                        new PlateComparisonInput(1, new BigDecimal("88.00"), 4000, new BigDecimal("100.00"), 4000)
                )
        );
        assertEquals(SlicingValidationStatus.REJECTED, moderateUnderRejectPolicy.overallStatus());
    }
}
