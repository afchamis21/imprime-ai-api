package org.imprime.ai.api.slicing;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.config.SlicingValidationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DockerCliSlicerEngine implements HeadlessSlicerEngine {

    private final SlicingValidationProperties properties;
    private final SliceOutputParser outputParser;

    @Override
    public HeadlessSliceResult slice(HeadlessSliceRequest request) {
        Path workDir = request.workDir().toAbsolutePath().normalize();
        String inName = request.input3mfPath().getFileName().toString();
        String procName = request.processSettingsPath().getFileName().toString();
        String outRel = workDir.relativize(request.outputDir().toAbsolutePath().normalize()).toString();
        String filName = request.filamentSettingsPath().getFileName().toString();

        List<String> command = List.of(
                "docker", "run", "--rm",
                "--network=none",
                "--cpus=2",
                "--memory=2g",
                "-v", workDir + ":/work:rw",
                properties.getDockerImage(),
                "--slice", "0",
                "--arrange", "1",
                "--ensure-on-bed",
                "--skip-modified-gcodes",
                "--skip-useless-pick",
                "--mstpp", String.valueOf(Math.max(10, request.timeoutSeconds())),
                "--mtcpp", String.valueOf(Math.max(100_000, request.maxTrianglesPerPlate())),
                "--load-settings", "/opt/slicer/profiles/machine/Bambu_Lab_A1_0.4_nozzle.json;/work/" + procName,
                "--load-filaments", "/opt/slicer/profiles/filament/" + filName,
                "--outputdir", "/work/" + outRel,
                "--export-3mf", "validated.3mf",
                "/work/" + inName
        );
        return BambuStudioCliSlicerEngine.executeCliProcess(command, request, "DOCKER_SLICER_CLI", outputParser);
    }
}
