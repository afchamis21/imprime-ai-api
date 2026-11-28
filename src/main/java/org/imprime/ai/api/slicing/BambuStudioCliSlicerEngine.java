package org.imprime.ai.api.slicing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.SlicingValidationProperties;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BambuStudioCliSlicerEngine implements HeadlessSlicerEngine {

    private static final int MAX_CAPTURED_LOG_BYTES = 64 * 1024;
    private static final List<String> FALLBACK_BINARY_CANDIDATES = List.of(
            "bambu-studio",
            "/etc/profiles/per-user/amorim/bin/bambu-studio",
            "/usr/local/bin/bambu-studio",
            "/usr/bin/bambu-studio",
            "/opt/slicer/bambu-studio"
    );

    private final SlicingValidationProperties properties;
    private final SliceOutputParser outputParser;

    @Override
    public HeadlessSliceResult slice(HeadlessSliceRequest request) {
        String executable = resolveExecutable(properties.getBinaryPath(), FALLBACK_BINARY_CANDIDATES);
        List<String> command = List.of(
                executable,
                "--slice", "0",
                "--arrange", "1",
                "--ensure-on-bed",
                "--skip-modified-gcodes",
                "--skip-useless-pick",
                "--mstpp", String.valueOf(Math.max(10, request.timeoutSeconds())),
                "--mtcpp", String.valueOf(Math.max(100_000, request.maxTrianglesPerPlate())),
                "--load-settings", request.machineSettingsPath() + ";" + request.processSettingsPath(),
                "--load-filaments", request.filamentSettingsPath().toString(),
                "--outputdir", request.outputDir().toString(),
                "--export-3mf", "validated.3mf",
                request.input3mfPath().toString()
        );
        return executeCliProcess(command, request, "BAMBU_STUDIO_CLI", outputParser);
    }

    static HeadlessSliceResult executeCliProcess(
            List<String> command,
            HeadlessSliceRequest request,
            String providerName,
            SliceOutputParser outputParser
    ) {
        try {
            Files.createDirectories(request.outputDir());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(request.workDir().toFile());
            pb.redirectErrorStream(true);
            pb.environment().putIfAbsent("QT_QPA_PLATFORM", "offscreen");

            Process process = pb.start();
            ByteArrayOutputStream logBuffer = new ByteArrayOutputStream();
            Thread reader = Thread.ofVirtual().start(() -> {
                try (InputStream in = process.getInputStream()) {
                    byte[] chunk = new byte[4096];
                    int read;
                    while ((read = in.read(chunk)) != -1) {
                        if (logBuffer.size() < MAX_CAPTURED_LOG_BYTES) {
                            logBuffer.write(chunk, 0, Math.min(read, MAX_CAPTURED_LOG_BYTES - logBuffer.size()));
                        }
                    }
                } catch (IOException ignored) {
                }
            });

            int waitSeconds = Math.max(15, request.timeoutSeconds() + 20);
            boolean finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1000);
                log.error("[{}] Headless slicing timed out after {}s", providerName, waitSeconds);
                throw new BadGatewayException("Tempo limite excedido no fatiamento do modelo no servidor.", MessageCd.SLICE_VALIDATION_FAILED);
            }
            reader.join(2000);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String tail = logBuffer.toString(StandardCharsets.UTF_8);
                log.error("[{}] CLI exited with code {}: {}", providerName, exitCode, tail);
                throw new BadGatewayException("Falha ao fatiar modelo no servidor (código " + exitCode + ").", MessageCd.SLICE_VALIDATION_FAILED);
            }

            Path exported3mf = request.outputDir().resolve("validated.3mf");
            Path gcodePath = request.outputDir().resolve("plate_1.gcode");
            return outputParser.parse(request.outputDir(), exported3mf, gcodePath, providerName);
        } catch (BadGatewayException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadGatewayException(MessageCd.SLICER_UNAVAILABLE);
        } catch (IOException ex) {
            log.error("[{}] Failed to launch headless slicer CLI: {}", providerName, ex.getMessage());
            throw new BadGatewayException("Binário do fatiador headless não disponível no servidor.", MessageCd.SLICER_UNAVAILABLE);
        }
    }

    static String resolveExecutable(String configuredPath, List<String> candidates) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            String trimmed = configuredPath.trim();
            if (trimmed.contains("/")) {
                if (Files.isExecutable(Path.of(trimmed))) {
                    return trimmed;
                }
            } else {
                String pathEnv = System.getenv("PATH");
                if (pathEnv != null) {
                    for (String dir : pathEnv.split(":")) {
                        if (!dir.isBlank() && Files.isExecutable(Path.of(dir, trimmed))) {
                            return Path.of(dir, trimmed).toString();
                        }
                    }
                }
            }
        }
        for (String candidate : candidates) {
            if (!candidate.contains("/")) {
                continue;
            }
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return configuredPath != null && !configuredPath.isBlank() ? configuredPath.trim() : candidates.getFirst();
    }
}
