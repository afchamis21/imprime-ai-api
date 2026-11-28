package org.imprime.ai.api.slicing;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.config.SlicingValidationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrcaSlicerCliSlicerEngine implements HeadlessSlicerEngine {

    private static final List<String> FALLBACK_BINARY_CANDIDATES = List.of(
            "orca-slicer",
            "/etc/profiles/per-user/amorim/bin/orca-slicer",
            "/usr/local/bin/orca-slicer",
            "/usr/bin/orca-slicer",
            "/opt/slicer/orca-slicer"
    );

    private final SlicingValidationProperties properties;
    private final SliceOutputParser outputParser;

    @Override
    public HeadlessSliceResult slice(HeadlessSliceRequest request) {
        String configured = properties.getBinaryPath();
        String binary = "bambu-studio".equals(configured) ? "orca-slicer" : configured;
        String executable = BambuStudioCliSlicerEngine.resolveExecutable(binary, FALLBACK_BINARY_CANDIDATES);

        List<String> command = List.of(
                executable,
                "--slice", "0",
                "--arrange", "1",
                "--ensure-on-bed",
                "--skip-modified-gcodes",
                "--mstpp", String.valueOf(Math.max(10, request.timeoutSeconds())),
                "--mtcpp", String.valueOf(Math.max(100_000, request.maxTrianglesPerPlate())),
                "--load-settings", request.machineSettingsPath() + ";" + request.processSettingsPath(),
                "--load-filaments", request.filamentSettingsPath().toString(),
                "--outputdir", request.outputDir().toString(),
                "--export-3mf", "validated.3mf",
                request.input3mfPath().toString()
        );
        return BambuStudioCliSlicerEngine.executeCliProcess(command, request, "ORCA_SLICER_CLI", outputParser);
    }
}
