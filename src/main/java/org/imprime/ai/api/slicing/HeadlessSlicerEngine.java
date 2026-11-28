package org.imprime.ai.api.slicing;

import java.math.BigDecimal;
import java.nio.file.Path;

public interface HeadlessSlicerEngine {

    record HeadlessSliceRequest(
            Path workDir,
            Path input3mfPath,
            Path machineSettingsPath,
            Path processSettingsPath,
            Path filamentSettingsPath,
            Path outputDir,
            int timeoutSeconds,
            int maxTrianglesPerPlate
    ) {
    }

    record HeadlessSliceResult(
            BigDecimal modelWeightGrams,
            BigDecimal supportWeightGrams,
            BigDecimal totalWeightGrams,
            int basePrintTimeSeconds,
            Path exported3mfPath,
            Path gcodePath,
            String providerName
    ) {
    }

    HeadlessSliceResult slice(HeadlessSliceRequest request);
}
