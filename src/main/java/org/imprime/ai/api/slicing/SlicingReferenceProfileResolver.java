package org.imprime.ai.api.slicing;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.config.SlicingValidationProperties;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.util.JsonCodec;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the canonical Bambu Lab A1 (0.4 mm nozzle) reference preset files bundled
 * from the frontend WASM runtime and creates per-job process overrides without
 * artificial benchmark slowdowns.
 */
@Component
@RequiredArgsConstructor
public class SlicingReferenceProfileResolver {

    public static final String REFERENCE_ENGINE_ID = "bambu";
    public static final String REFERENCE_PROFILE_ID = "Bambu Lab A1 0.4 nozzle";

    private static final String CLASSPATH_PREFIX = "slicer-profiles/bambu-a1/";
    private static final String MACHINE_REL_PATH = "machine/Bambu_Lab_A1_0.4_nozzle.json";
    private static final String PROCESS_REL_PATH = "process/0.20mm_Standard_BBL_A1.json";
    private static final List<String> BUNDLED_FILES = List.of(
            MACHINE_REL_PATH,
            PROCESS_REL_PATH,
            "filament/PLA.json",
            "filament/PETG.json",
            "filament/ABS.json",
            "filament/ASA.json",
            "filament/TPU.json",
            "filament/NYLON.json",
            "filament/PC.json",
            "filament/PA_CF.json",
            "filament/PETG_CF.json",
            "filament/PVA.json",
            "filament/HIPS.json"
    );

    private final SlicingValidationProperties properties;
    private final JsonCodec jsonCodec;
    private volatile @Nullable Path cachedExtractedRoot;

    public record ProcessParameters(
            @Nullable BigDecimal layerHeightMm,
            @Nullable Integer infillPercent,
            @Nullable String infillPattern,
            @Nullable Integer wallCount,
            @Nullable Boolean supports,
            @Nullable String supportType,
            @Nullable String adhesionType
    ) {
    }

    public record ResolvedPresetPaths(
            Path machineSettingsPath,
            Path processSettingsPath,
            Path filamentSettingsPath
    ) {
    }

    public synchronized Path resolveProfilesRoot() throws IOException {
        String configured = properties.getProfilesDir();
        if (configured != null && !configured.isBlank()) {
            Path custom = Path.of(configured.trim());
            if (Files.isDirectory(custom) && Files.isRegularFile(custom.resolve(MACHINE_REL_PATH))) {
                return custom;
            }
        }

        if (cachedExtractedRoot != null && Files.isRegularFile(cachedExtractedRoot.resolve(MACHINE_REL_PATH))) {
            return cachedExtractedRoot;
        }

        Path tempRoot = Files.createTempDirectory("imprime-bambu-a1-profiles-");
        tempRoot.toFile().deleteOnExit();
        for (String rel : BUNDLED_FILES) {
            Path target = tempRoot.resolve(rel);
            Files.createDirectories(target.getParent());
            ClassPathResource resource = new ClassPathResource(CLASSPATH_PREFIX + rel);
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        cachedExtractedRoot = tempRoot;
        return tempRoot;
    }

    public ResolvedPresetPaths prepareJobPresets(
            Path jobDir,
            MaterialType material,
            ProcessParameters params
    ) throws IOException {
        Path root = resolveProfilesRoot();
        Path machinePath = root.resolve(MACHINE_REL_PATH);
        Path baseProcessPath = root.resolve(PROCESS_REL_PATH);
        Path filamentPath = root.resolve(resolveFilamentRelPath(material));
        if (!Files.isRegularFile(filamentPath)) {
            filamentPath = root.resolve("filament/PLA.json");
        }

        Path jobProcessPath = jobDir.resolve("process_override.json");
        String baseProcessJson = Files.readString(baseProcessPath);
        JsonNode parsed = jsonCodec.readTree(baseProcessJson);
        ObjectNode node = parsed instanceof ObjectNode obj
                ? obj.deepCopy()
                : jsonCodec.createObjectNode();

        applyProcessOverrides(node, params);
        Files.writeString(jobProcessPath, jsonCodec.toJson(node));

        return new ResolvedPresetPaths(machinePath, jobProcessPath, filamentPath);
    }

    private void applyProcessOverrides(ObjectNode node, ProcessParameters params) {
        if (properties.getProvider() == SlicingValidationProperties.SlicerProvider.ORCA_SLICER) {
            node.put("solid_infill_filament", "1");
            node.put("sparse_infill_filament", "1");
            node.put("wall_filament", "1");
            node.put("tree_support_wall_count", "0");
        }
        if (params == null) {
            return;
        }
        if (params.layerHeightMm() != null && params.layerHeightMm().signum() > 0) {
            String layer = params.layerHeightMm().stripTrailingZeros().toPlainString();
            node.put("layer_height", layer);
            node.put("initial_layer_print_height", layer);
        }
        if (params.infillPercent() != null) {
            int clamped = Math.max(0, Math.min(100, params.infillPercent()));
            node.put("sparse_infill_density", clamped + "%");
        }
        if (params.infillPattern() != null && !params.infillPattern().isBlank()) {
            node.put("sparse_infill_pattern", mapInfillPattern(params.infillPattern()));
        }
        if (params.wallCount() != null && params.wallCount() > 0) {
            node.put("wall_loops", String.valueOf(params.wallCount()));
        }
        if (params.supports() != null) {
            node.put("enable_support", Boolean.TRUE.equals(params.supports()) ? "1" : "0");
            if (Boolean.TRUE.equals(params.supports())) {
                String sup = params.supportType() != null ? params.supportType().trim().toLowerCase(Locale.ROOT) : "";
                if ("tree".equals(sup)) {
                    node.put("support_type", "tree(auto)");
                    node.put("support_on_build_plate_only", "0");
                } else {
                    node.put("support_type", "normal(auto)");
                    node.put("support_on_build_plate_only", "buildplate".equals(sup) ? "1" : "0");
                }
            }
        }
        if (params.adhesionType() != null && !params.adhesionType().isBlank()) {
            String adhesion = params.adhesionType().trim().toLowerCase(Locale.ROOT);
            switch (adhesion) {
                case "brim" -> {
                    node.put("brim_type", "outer_only");
                    node.put("brim_width", "5");
                    node.put("skirt_loops", "0");
                    node.put("raft_layers", "0");
                }
                case "skirt" -> {
                    node.put("brim_type", "no_brim");
                    node.put("brim_width", "0");
                    node.put("skirt_loops", "1");
                    node.put("raft_layers", "0");
                }
                case "raft" -> {
                    node.put("brim_type", "no_brim");
                    node.put("brim_width", "0");
                    node.put("skirt_loops", "0");
                    node.put("raft_layers", "2");
                }
                default -> {
                    node.put("brim_type", "no_brim");
                    node.put("brim_width", "0");
                    node.put("skirt_loops", "0");
                    node.put("raft_layers", "0");
                }
            }
        }
    }

    private static String mapInfillPattern(String pattern) {
        return switch (pattern.trim().toLowerCase(Locale.ROOT)) {
            case "triangles", "triangle" -> "triangle";
            case "gyroid" -> "gyroid";
            case "cubic" -> "cubic";
            default -> "grid";
        };
    }

    public static String resolveFilamentRelPath(@Nullable MaterialType material) {
        if (material == null) {
            return "filament/PLA.json";
        }
        return switch (material) {
            case PLA -> "filament/PLA.json";
            case PETG -> "filament/PETG.json";
            case ABS -> "filament/ABS.json";
            case ASA -> "filament/ASA.json";
            case TPU -> "filament/TPU.json";
            case NYLON -> "filament/NYLON.json";
            case PC -> "filament/PC.json";
            case PA_CF, CARBON_FIBER -> "filament/PA_CF.json";
            case PETG_CF -> "filament/PETG_CF.json";
            case PVA -> "filament/PVA.json";
            case HIPS -> "filament/HIPS.json";
            default -> "filament/PLA.json";
        };
    }
}
