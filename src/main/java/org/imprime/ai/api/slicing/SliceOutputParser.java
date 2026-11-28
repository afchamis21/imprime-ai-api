package org.imprime.ai.api.slicing;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.slicing.HeadlessSlicerEngine.HeadlessSliceResult;
import org.imprime.ai.api.util.JsonCodec;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.jackson.databind.JsonNode;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses model weight, support weight, total weight, and estimated print time from
 * Bambu Studio / OrcaSlicer headless outputs (result.json, Metadata/slice_info.config,
 * and plate G-code headers/features) with strict XML XXE protections.
 */
@Component
@RequiredArgsConstructor
public class SliceOutputParser {

    private static final Pattern TOTAL_WEIGHT_GCODE_PATTERN =
            Pattern.compile("(?m)^;\\s*total filament weight \\[g]\\s*:\\s*([0-9.,\\s]+)$");
    private static final Pattern TOTAL_TIME_GCODE_PATTERN =
            Pattern.compile("(?m)^;\\s*total estimated time:\\s*(.+)$");
    private static final Pattern MODEL_TIME_GCODE_PATTERN =
            Pattern.compile("(?m);\\s*model printing time:\\s*([^;\\r\\n]+)");
    private static final Pattern TIME_PART_PATTERN =
            Pattern.compile("(?:(\\d+)d\\s*)?(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s)?");
    private static final Pattern FILAMENT_DENSITY_PATTERN =
            Pattern.compile("(?m)^;\\s*filament_density:\\s*([0-9.,]+)");
    private static final Pattern FILAMENT_DIAMETER_PATTERN =
            Pattern.compile("(?m)^;\\s*filament_diameter:\\s*([0-9.,]+)");

    private final JsonCodec jsonCodec;

    public HeadlessSliceResult parse(
            Path outputDir,
            @Nullable Path exported3mfPath,
            @Nullable Path gcodePath,
            String providerName
    ) {
        Path resolvedGcode = gcodePath != null && Files.isRegularFile(gcodePath)
                ? gcodePath
                : findFirstGcode(outputDir);
        Path resolved3mf = exported3mfPath != null && Files.isRegularFile(exported3mfPath)
                ? exported3mfPath
                : findFirst3mf(outputDir);

        Path resultJsonPath = outputDir.resolve("result.json");
        if (Files.isRegularFile(resultJsonPath)) {
            HeadlessSliceResult fromJson = parseResultJson(
                    resultJsonPath, resolved3mf, resolvedGcode, providerName
            );
            if (fromJson != null) {
                return fromJson;
            }
        }

        if (resolved3mf != null && Files.isRegularFile(resolved3mf)) {
            HeadlessSliceResult from3mf = parse3mfSliceInfo(
                    resolved3mf, resolvedGcode, providerName
            );
            if (from3mf != null) {
                return from3mf;
            }
        }

        if (resolvedGcode != null && Files.isRegularFile(resolvedGcode)) {
            HeadlessSliceResult fromGcode = parseGcodeOnly(
                    resolvedGcode, resolved3mf, providerName
            );
            if (fromGcode != null) {
                return fromGcode;
            }
        }

        throw new BadGatewayException(MessageCd.SLICE_VALIDATION_FAILED);
    }

    private @Nullable HeadlessSliceResult parseResultJson(
            Path resultJsonPath,
            @Nullable Path exported3mfPath,
            @Nullable Path gcodePath,
            String providerName
    ) {
        try {
            String raw = Files.readString(resultJsonPath, StandardCharsets.UTF_8);
            JsonNode root = jsonCodec.readTree(raw);
            if (root == null) {
                return null;
            }
            if (root.has("return_code") && root.get("return_code").asInt(0) != 0) {
                return null;
            }
            JsonNode plates = root.get("sliced_plates");
            if (plates == null || !plates.isArray() || plates.isEmpty()) {
                return null;
            }
            JsonNode plate = plates.get(0);
            double totalUsed = 0.0d;
            double mainUsed = 0.0d;
            boolean hasMainUsed = false;

            JsonNode filaments = plate.get("filaments");
            if (filaments != null && filaments.isArray()) {
                for (JsonNode fil : filaments) {
                    if (fil.has("total_used_g")) {
                        totalUsed += Math.max(0.0d, fil.get("total_used_g").asDouble(0.0d));
                    }
                    if (fil.has("main_used_g")) {
                        hasMainUsed = true;
                        mainUsed += Math.max(0.0d, fil.get("main_used_g").asDouble(0.0d));
                    }
                }
            }

            if (totalUsed <= 0.0d) {
                return null;
            }

            double modelG = hasMainUsed ? Math.min(totalUsed, mainUsed) : totalUsed;
            double supportG = Math.max(0.0d, totalUsed - modelG);

            double prediction = plate.has("main_predication") && plate.get("main_predication").asDouble(0.0d) > 0.0d
                    ? plate.get("main_predication").asDouble(0.0d)
                    : (plate.has("total_predication") ? plate.get("total_predication").asDouble(0.0d) : 0.0d);
            int baseSeconds = Math.max(1, (int) Math.round(prediction));

            return new HeadlessSliceResult(
                    scale2(modelG),
                    scale2(supportG),
                    scale2(totalUsed),
                    baseSeconds,
                    exported3mfPath,
                    gcodePath,
                    providerName
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    public @Nullable HeadlessSliceResult parse3mfSliceInfo(
            Path threeMfPath,
            @Nullable Path externalGcodePath,
            String providerName
    ) {
        byte[] sliceInfoXml = null;
        String embeddedGcode = null;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(threeMfPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String lower = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if ("metadata/slice_info.config".equals(lower)) {
                    sliceInfoXml = zis.readAllBytes();
                } else if (externalGcodePath == null && lower.startsWith("metadata/") && lower.endsWith(".gcode")) {
                    embeddedGcode = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ignored) {
            return null;
        }

        if (sliceInfoXml == null || sliceInfoXml.length == 0) {
            return null;
        }

        if (embeddedGcode == null && externalGcodePath != null && Files.isRegularFile(externalGcodePath)) {
            try {
                embeddedGcode = Files.readString(externalGcodePath, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }

        return parseSliceInfoXmlBytes(sliceInfoXml, embeddedGcode, threeMfPath, externalGcodePath, providerName);
    }

    public @Nullable HeadlessSliceResult parseSliceInfoXmlBytes(
            byte[] sliceInfoXml,
            @Nullable String gcodeText,
            @Nullable Path exported3mfPath,
            @Nullable Path gcodePath,
            String providerName
    ) {
        try {
            DocumentBuilderFactory factory = BambuProjectPacker.newSecureDocumentBuilderFactory();
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(sliceInfoXml));

            double totalWeight = 0.0d;
            int predictionSeconds = 0;

            NodeList metadataNodes = doc.getElementsByTagName("metadata");
            for (int i = 0; i < metadataNodes.getLength(); i++) {
                if (metadataNodes.item(i) instanceof Element el) {
                    String key = el.getAttribute("key");
                    String val = el.getAttribute("value");
                    if ("prediction".equalsIgnoreCase(key) && val != null && !val.isBlank()) {
                        predictionSeconds = Math.max(predictionSeconds, (int) Math.round(Double.parseDouble(val.trim())));
                    } else if ("weight".equalsIgnoreCase(key) && val != null && !val.isBlank()) {
                        totalWeight = Math.max(totalWeight, Double.parseDouble(val.trim()));
                    }
                }
            }

            double dedicatedSupportG = 0.0d;
            double dedicatedObjectG = 0.0d;
            boolean sawSeparateSupportFilament = false;

            NodeList filamentNodes = doc.getElementsByTagName("filament");
            for (int i = 0; i < filamentNodes.getLength(); i++) {
                if (filamentNodes.item(i) instanceof Element el) {
                    String usedAttr = el.getAttribute("used_g");
                    if (usedAttr == null || usedAttr.isBlank()) {
                        continue;
                    }
                    double usedG = Double.parseDouble(usedAttr.trim());
                    boolean forObj = Boolean.parseBoolean(el.getAttribute("used_for_object"));
                    boolean forSup = Boolean.parseBoolean(el.getAttribute("used_for_support"));
                    if (forSup && !forObj) {
                        sawSeparateSupportFilament = true;
                        dedicatedSupportG += usedG;
                    } else if (forObj && !forSup) {
                        dedicatedObjectG += usedG;
                    }
                }
            }

            double supportG = 0.0d;
            double modelG = totalWeight;
            if (gcodeText != null && !gcodeText.isBlank()) {
                int modelPrintingSec = extractModelSecondsFromGcode(gcodeText);
                if (modelPrintingSec > 0) {
                    predictionSeconds = modelPrintingSec;
                }
            }
            if (sawSeparateSupportFilament) {
                supportG = Math.min(totalWeight, dedicatedSupportG);
                modelG = Math.max(0.0d, totalWeight - supportG);
            } else if (gcodeText != null && !gcodeText.isBlank()) {
                double gcodeSupportG = estimateSupportWeightFromGcode(gcodeText);
                if (gcodeSupportG > 0.0d) {
                    supportG = Math.min(totalWeight, gcodeSupportG);
                    modelG = Math.max(0.0d, totalWeight - supportG);
                }
            }

            if (totalWeight <= 0.0d || predictionSeconds <= 0) {
                return null;
            }

            return new HeadlessSliceResult(
                    scale2(modelG),
                    scale2(supportG),
                    scale2(totalWeight),
                    predictionSeconds,
                    exported3mfPath,
                    gcodePath,
                    providerName
            );
        } catch (Exception e) {
            throw new BadGatewayException("XML de saída do fatiador inválido ou bloqueado.", MessageCd.SLICE_VALIDATION_FAILED);
        }
    }

    private @Nullable HeadlessSliceResult parseGcodeOnly(
            Path gcodePath,
            @Nullable Path exported3mfPath,
            String providerName
    ) {
        try {
            String gcode = Files.readString(gcodePath, StandardCharsets.UTF_8);
            double totalWeight = extractTotalWeightFromGcode(gcode);
            int totalSeconds = extractTotalSecondsFromGcode(gcode);
            if (totalWeight <= 0.0d || totalSeconds <= 0) {
                return null;
            }
            double supportG = Math.min(totalWeight, estimateSupportWeightFromGcode(gcode));
            double modelG = Math.max(0.0d, totalWeight - supportG);
            return new HeadlessSliceResult(
                    scale2(modelG),
                    scale2(supportG),
                    scale2(totalWeight),
                    totalSeconds,
                    exported3mfPath,
                    gcodePath,
                    providerName
            );
        } catch (IOException ignored) {
            return null;
        }
    }

    static double extractTotalWeightFromGcode(String gcode) {
        Matcher m = TOTAL_WEIGHT_GCODE_PATTERN.matcher(gcode);
        if (!m.find()) {
            return 0.0d;
        }
        String raw = m.group(1);
        double sum = 0.0d;
        for (String token : raw.split(",")) {
            String cleaned = token.trim();
            if (!cleaned.isEmpty()) {
                sum += Double.parseDouble(cleaned);
            }
        }
        return sum;
    }

    static int extractModelSecondsFromGcode(String gcode) {
        Matcher m = MODEL_TIME_GCODE_PATTERN.matcher(gcode);
        if (!m.find()) {
            return 0;
        }
        return parseDurationString(m.group(1).trim());
    }

    static int extractTotalSecondsFromGcode(String gcode) {
        int modelTime = extractModelSecondsFromGcode(gcode);
        if (modelTime > 0) {
            return modelTime;
        }
        Matcher m = TOTAL_TIME_GCODE_PATTERN.matcher(gcode);
        if (!m.find()) {
            return 0;
        }
        return parseDurationString(m.group(1).trim());
    }

    private static int parseDurationString(String timeStr) {
        Matcher part = TIME_PART_PATTERN.matcher(timeStr);
        if (!part.find()) {
            return 0;
        }
        int d = parseGroupInt(part.group(1));
        int h = parseGroupInt(part.group(2));
        int min = parseGroupInt(part.group(3));
        int s = parseGroupInt(part.group(4));
        return d * 86400 + h * 3600 + min * 60 + s;
    }

    static double estimateSupportWeightFromGcode(String gcode) {
        double density = 1.24d;
        Matcher dm = FILAMENT_DENSITY_PATTERN.matcher(gcode);
        if (dm.find()) {
            density = Double.parseDouble(dm.group(1).replace(',', '.'));
        }
        double diameter = 1.75d;
        Matcher diamM = FILAMENT_DIAMETER_PATTERN.matcher(gcode);
        if (diamM.find()) {
            diameter = Double.parseDouble(diamM.group(1).replace(',', '.'));
        }
        double radius = diameter / 2.0d;
        double gramsPerMm = Math.PI * radius * radius * (density / 1000.0d);

        boolean relativeE = true;
        boolean inSupport = false;
        double currentE = 0.0d;
        double supportMm = 0.0d;

        String[] lines = gcode.split("\\r?\\n");
        for (String line : lines) {
            if (line.startsWith("; FEATURE:")) {
                inSupport = line.contains("Support");
                continue;
            }
            if (line.startsWith("M82")) {
                relativeE = false;
                continue;
            }
            if (line.startsWith("M83")) {
                relativeE = true;
                continue;
            }
            if (line.startsWith("G92")) {
                Double eVal = extractEParam(line);
                if (eVal != null) {
                    currentE = eVal;
                }
                continue;
            }
            if (line.startsWith("G1 ") || line.startsWith("G0 ")) {
                Double eVal = extractEParam(line);
                if (eVal != null) {
                    if (relativeE) {
                        if (inSupport && eVal > 0.0d) {
                            supportMm += eVal;
                        }
                    } else {
                        double delta = eVal - currentE;
                        currentE = eVal;
                        if (inSupport && delta > 0.0d) {
                            supportMm += delta;
                        }
                    }
                }
            }
        }
        return Math.max(0.0d, supportMm * gramsPerMm);
    }

    private static @Nullable Double extractEParam(String line) {
        int idx = line.indexOf(" E");
        if (idx < 0) {
            return null;
        }
        int end = idx + 2;
        while (end < line.length()) {
            char c = line.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+') {
                end++;
            } else {
                break;
            }
        }
        if (end <= idx + 2) {
            return null;
        }
        try {
            return Double.parseDouble(line.substring(idx + 2, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseGroupInt(@Nullable String g) {
        return g == null || g.isBlank() ? 0 : Integer.parseInt(g);
    }

    private static @Nullable Path findFirstGcode(Path outputDir) {
        try (var stream = Files.list(outputDir)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gcode"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static @Nullable Path findFirst3mf(Path outputDir) {
        try (var stream = Files.list(outputDir)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".3mf"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static BigDecimal scale2(double value) {
        return BigDecimal.valueOf(Math.max(0.0d, value)).setScale(2, RoundingMode.HALF_UP);
    }
}
