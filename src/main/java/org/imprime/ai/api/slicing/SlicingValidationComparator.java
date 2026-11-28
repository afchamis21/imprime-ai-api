package org.imprime.ai.api.slicing;

import org.imprime.ai.api.config.SlicingValidationProperties;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure comparator enforcing configurable weight and print time tolerance bands
 * and resolving the overall validation outcome (VALIDATED, RECALCULATED, REJECTED).
 */
@Component
public class SlicingValidationComparator {

    public record PlateComparisonInput(
            int plateNumber,
            BigDecimal clientTotalWeightGrams,
            int clientBasePrintTimeSeconds,
            BigDecimal serverTotalWeightGrams,
            int serverBasePrintTimeSeconds
    ) {
    }

    public record PlateComparisonResult(
            int plateNumber,
            BigDecimal weightDivergencePercent,
            BigDecimal timeDivergencePercent,
            boolean weightWithinTolerance,
            boolean timeWithinTolerance,
            boolean hardRejected,
            SlicingValidationStatus plateStatus
    ) {
    }

    public record OrderComparisonSummary(
            SlicingValidationStatus overallStatus,
            BigDecimal maxWeightDivergencePercent,
            BigDecimal maxTimeDivergencePercent,
            List<PlateComparisonResult> plates
    ) {
    }

    public OrderComparisonSummary compare(
            SlicingValidationProperties.Validation config,
            List<PlateComparisonInput> plates
    ) {
        double weightPct = Math.max(0.0d, config.getWeightTolerancePercent());
        double minWeightFloor = Math.max(0.0d, config.getMinWeightToleranceGrams());
        double timePct = Math.max(0.0d, config.getTimeTolerancePercent());
        int minTimeFloor = Math.max(0, config.getMinTimeToleranceSeconds());
        double hardRejectPct = Math.max(weightPct, config.getHardRejectTolerancePercent());

        boolean anyHardRejected = false;
        boolean anyOutsideTolerance = false;
        BigDecimal maxWeightDiv = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxTimeDiv = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<PlateComparisonResult> results = new java.util.ArrayList<>(plates.size());

        for (PlateComparisonInput plate : plates) {
            double serverW = plate.serverTotalWeightGrams() != null ? plate.serverTotalWeightGrams().doubleValue() : 0.0d;
            double clientW = plate.clientTotalWeightGrams() != null ? plate.clientTotalWeightGrams().doubleValue() : 0.0d;
            double deltaW = Math.abs(serverW - clientW);
            double allowedW = Math.max(serverW * (weightPct / 100.0d), minWeightFloor);
            boolean weightOk = deltaW <= allowedW;
            double weightDivVal = serverW > 0.0d ? (deltaW / serverW) * 100.0d : (deltaW > 0.0d ? 100.0d : 0.0d);

            int serverT = Math.max(0, plate.serverBasePrintTimeSeconds());
            int clientT = Math.max(0, plate.clientBasePrintTimeSeconds());
            int deltaT = Math.abs(serverT - clientT);
            double allowedT = Math.max(serverT * (timePct / 100.0d), minTimeFloor);
            boolean timeOk = deltaT <= allowedT;
            double timeDivVal = serverT > 0 ? ((double) deltaT / serverT) * 100.0d : (deltaT > 0 ? 100.0d : 0.0d);

            boolean weightHardReject = !weightOk && weightDivVal > hardRejectPct;
            boolean timeHardReject = !timeOk && timeDivVal > hardRejectPct;
            boolean hardRejected = weightHardReject || timeHardReject;

            SlicingValidationStatus plateStatus;
            if (weightOk && timeOk) {
                plateStatus = SlicingValidationStatus.VALIDATED;
            } else if (hardRejected || config.getDivergencePolicy() == SlicingValidationProperties.DivergencePolicy.REJECT) {
                plateStatus = SlicingValidationStatus.REJECTED;
                anyHardRejected = true;
                anyOutsideTolerance = true;
            } else {
                plateStatus = SlicingValidationStatus.RECALCULATED;
                anyOutsideTolerance = true;
            }

            BigDecimal wDivBd = BigDecimal.valueOf(Math.min(999.99d, weightDivVal)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tDivBd = BigDecimal.valueOf(Math.min(999.99d, timeDivVal)).setScale(2, RoundingMode.HALF_UP);
            if (wDivBd.compareTo(maxWeightDiv) > 0) {
                maxWeightDiv = wDivBd;
            }
            if (tDivBd.compareTo(maxTimeDiv) > 0) {
                maxTimeDiv = tDivBd;
            }

            results.add(new PlateComparisonResult(
                    plate.plateNumber(),
                    wDivBd,
                    tDivBd,
                    weightOk,
                    timeOk,
                    hardRejected,
                    plateStatus
            ));
        }

        SlicingValidationStatus overall;
        if (anyHardRejected || (anyOutsideTolerance && config.getDivergencePolicy() == SlicingValidationProperties.DivergencePolicy.REJECT)) {
            overall = SlicingValidationStatus.REJECTED;
        } else if (anyOutsideTolerance) {
            overall = SlicingValidationStatus.RECALCULATED;
        } else {
            overall = SlicingValidationStatus.VALIDATED;
        }

        return new OrderComparisonSummary(overall, maxWeightDiv, maxTimeDiv, results);
    }
}
