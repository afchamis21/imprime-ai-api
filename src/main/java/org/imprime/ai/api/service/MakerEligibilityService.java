package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.maker.BuildVolumeDto;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.util.JsonCodec;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Avalia se um Maker possui perfil e parque de máquinas compatíveis com os requisitos
 * técnicos das peças enviadas em {@code /print} antes de exibi-lo em {@code /makers}.
 */
@Service
@RequiredArgsConstructor
public class MakerEligibilityService {
    private static final BigDecimal DEFAULT_BUILD_VOLUME_MM = new BigDecimal("256.00");

    private final JsonCodec jsonCodec;

    public boolean isEligible(
            Company company,
            List<Asset> activePrinters,
            Map<Long, PrinterCatalog> catalogById,
            List<OrderFileAsset> plates
    ) {
        if (company == null) {
            return false;
        }
        if (company.getStatus() != null && company.getStatus() != StatusCd.ACTIVE) {
            return false;
        }
        if (company.getApprovalStatus() != null && company.getApprovalStatus() != MakerApprovalStatus.APPROVED) {
            return false;
        }
        if (Boolean.TRUE.equals(company.getVacationMode())) {
            return false;
        }
        if (Boolean.FALSE.equals(company.getAcceptingCustomQuotes())) {
            return false;
        }

        if (plates == null || plates.isEmpty()) {
            return true;
        }

        List<Asset> printers = activePrinters == null
                ? List.of()
                : activePrinters.stream()
                .filter(Objects::nonNull)
                .filter(a -> a.getStatus() == null || a.getStatus() == StatusCd.ACTIVE)
                .toList();

        List<MakerCapability> capabilities = jsonCodec.readEnumList(company.getCapabilitiesJson(), MakerCapability.class);
        boolean companySupportsMulticolor = Boolean.TRUE.equals(company.getSupportsMulticolor())
                || capabilities.contains(MakerCapability.MULTICOLOR_AMS);
        int companyMaxSlots = resolveCompanyMaxSlots(company, companySupportsMulticolor);
        boolean fleetHasExplicitMulticolorPrinters = printers.stream()
                .anyMatch(a -> Boolean.TRUE.equals(a.getSupportsMulticolor()));

        for (OrderFileAsset plate : plates) {
            if (plate == null) {
                continue;
            }
            boolean plateNeedsMulticolor = isPlateMulticolor(plate);
            int requiredColors = resolveRequiredColors(plate, plateNeedsMulticolor);
            boolean plateHasDimensions = hasPositiveDimensions(plate);

            if (plateNeedsMulticolor) {
                if (!companySupportsMulticolor && !fleetHasExplicitMulticolorPrinters) {
                    return false;
                }
                if (printers.isEmpty() && companyMaxSlots < requiredColors) {
                    return false;
                }
            }

            if (plateNeedsMulticolor || plateHasDimensions) {
                if (printers.isEmpty()) {
                    if (plateHasDimensions && !fitsFallbackVolume(plate)) {
                        return false;
                    }
                } else {
                    boolean anyPrinterCanFulfillPlate = printers.stream().anyMatch(printer -> {
                        PrinterCatalog catalog = resolveCatalog(printer, catalogById);
                        if (plateHasDimensions && !fitsDimensionsWithRotation(printer, catalog, plate)) {
                            return false;
                        }
                        if (plateNeedsMulticolor) {
                            if (fleetHasExplicitMulticolorPrinters) {
                                return Boolean.TRUE.equals(printer.getSupportsMulticolor())
                                        && resolvePrinterSlots(printer, catalog, companyMaxSlots) >= requiredColors;
                            }
                            return companySupportsMulticolor && companyMaxSlots >= requiredColors;
                        }
                        return true;
                    });
                    if (!anyPrinterCanFulfillPlate) {
                        return false;
                    }
                }
            }
        }

        /*
         * =========================================================================
         * VALIDAÇÃO POR MATERIAL TEMPORARIAMENTE DESATIVADA
         * A lógica completa de verificação de materiais suportados pela impressora
         * do Maker está implementada em supportsRequiredMaterials(...) e validada
         * em MakerEligibilityServiceTest.
         *
         * Para reativar a filtragem automática por material em /print -> /makers,
         * basta descomentar o bloco abaixo:
         *
         * if (!supportsRequiredMaterials(company, printers, catalogById, plates)) {
         *     return false;
         * }
         * =========================================================================
         */

        return true;
    }

    /**
     * Valida se o Maker / suas impressoras suportam todos os materiais exigidos pelas mesas do pedido.
     */
    public boolean supportsRequiredMaterials(
            Company company,
            List<Asset> activePrinters,
            Map<Long, PrinterCatalog> catalogById,
            List<OrderFileAsset> plates
    ) {
        if (company == null || plates == null || plates.isEmpty()) {
            return true;
        }

        List<Asset> printers = activePrinters == null
                ? List.of()
                : activePrinters.stream()
                .filter(Objects::nonNull)
                .filter(a -> a.getStatus() == null || a.getStatus() == StatusCd.ACTIVE)
                .toList();

        Set<MaterialType> companyMaterials = new LinkedHashSet<>(
                jsonCodec.readEnumList(company.getMaterialsJson(), MaterialType.class)
        );

        for (OrderFileAsset plate : plates) {
            if (plate == null || plate.getMaterial() == null || plate.getMaterial().isBlank()) {
                continue;
            }
            MaterialType requiredMaterial = parseMaterial(plate.getMaterial());
            if (requiredMaterial == null) {
                continue;
            }

            boolean plateHasDimensions = hasPositiveDimensions(plate);
            boolean plateNeedsMulticolor = isPlateMulticolor(plate);

            if (printers.isEmpty()) {
                if (!companyMaterials.contains(requiredMaterial)) {
                    return false;
                }
                continue;
            }

            boolean anyPrinterSupportsMaterial = printers.stream().anyMatch(printer -> {
                PrinterCatalog catalog = resolveCatalog(printer, catalogById);
                if (plateHasDimensions && !fitsDimensionsWithRotation(printer, catalog, plate)) {
                    return false;
                }
                if (plateNeedsMulticolor && !Boolean.TRUE.equals(printer.getSupportsMulticolor())
                        && !Boolean.TRUE.equals(company.getSupportsMulticolor())) {
                    return false;
                }
                Set<MaterialType> printerMaterials = resolvePrinterSupportedMaterials(printer, catalog, companyMaterials);
                return printerMaterials.contains(requiredMaterial);
            });

            if (!anyPrinterSupportsMaterial) {
                return false;
            }
        }

        return true;
    }

    public BuildVolumeDto computeMaxBuildVolume(List<Asset> activePrinters, Map<Long, PrinterCatalog> catalogById) {
        if (activePrinters == null || activePrinters.isEmpty()) {
            return null;
        }
        Asset bestAsset = null;
        BigDecimal bestVolume = BigDecimal.ZERO;
        BigDecimal bestX = null;
        BigDecimal bestY = null;
        BigDecimal bestZ = null;

        for (Asset asset : activePrinters) {
            if (asset == null || (asset.getStatus() != null && asset.getStatus() != StatusCd.ACTIVE)) {
                continue;
            }
            PrinterCatalog catalog = resolveCatalog(asset, catalogById);
            BigDecimal vx = resolveDimension(asset.getTrayWidth(), catalog != null ? catalog.getBuildVolumeXMm() : null);
            BigDecimal vy = resolveDimension(asset.getTrayDepth(), catalog != null ? catalog.getBuildVolumeYMm() : null);
            BigDecimal vz = resolveDimension(asset.getTrayHeight(), catalog != null ? catalog.getBuildVolumeZMm() : null);
            if (vx == null || vy == null || vz == null) {
                continue;
            }
            BigDecimal vol = vx.multiply(vy).multiply(vz);
            if (bestAsset == null || vol.compareTo(bestVolume) > 0) {
                bestAsset = asset;
                bestVolume = vol;
                bestX = vx;
                bestY = vy;
                bestZ = vz;
            }
        }
        return bestAsset != null
                ? new BuildVolumeDto(bestX.intValue(), bestY.intValue(), bestZ.intValue())
                : null;
    }

    private Set<MaterialType> resolvePrinterSupportedMaterials(
            Asset printer,
            PrinterCatalog catalog,
            Set<MaterialType> companyFallbackMaterials
    ) {
        List<MaterialType> override = jsonCodec.readEnumList(printer.getSupportedMaterialsOverrideJson(), MaterialType.class);
        if (override != null && !override.isEmpty()) {
            return new LinkedHashSet<>(override);
        }
        if (catalog != null && catalog.getSupportedMaterials() != null && !catalog.getSupportedMaterials().isEmpty()) {
            return catalog.getSupportedMaterials();
        }
        return companyFallbackMaterials;
    }

    private boolean fitsDimensionsWithRotation(Asset printer, PrinterCatalog catalog, OrderFileAsset plate) {
        BigDecimal vx = resolveDimension(printer.getTrayWidth(), catalog != null ? catalog.getBuildVolumeXMm() : null);
        BigDecimal vy = resolveDimension(printer.getTrayDepth(), catalog != null ? catalog.getBuildVolumeYMm() : null);
        BigDecimal vz = resolveDimension(printer.getTrayHeight(), catalog != null ? catalog.getBuildVolumeZMm() : null);
        if (vx == null || vy == null || vz == null) {
            return false;
        }
        return fitsBoxWithRotation(plate.getDimensionXMm(), plate.getDimensionYMm(), plate.getDimensionZMm(), vx, vy, vz);
    }

    private boolean fitsFallbackVolume(OrderFileAsset plate) {
        return fitsBoxWithRotation(
                plate.getDimensionXMm(),
                plate.getDimensionYMm(),
                plate.getDimensionZMm(),
                DEFAULT_BUILD_VOLUME_MM,
                DEFAULT_BUILD_VOLUME_MM,
                DEFAULT_BUILD_VOLUME_MM
        );
    }

    private static boolean fitsBoxWithRotation(
            BigDecimal px,
            BigDecimal py,
            BigDecimal pz,
            BigDecimal vx,
            BigDecimal vy,
            BigDecimal vz
    ) {
        if (pz.compareTo(vz) > 0) {
            return false;
        }
        boolean unrotated = px.compareTo(vx) <= 0 && py.compareTo(vy) <= 0;
        boolean rotated90 = py.compareTo(vx) <= 0 && px.compareTo(vy) <= 0;
        return unrotated || rotated90;
    }

    private static boolean hasPositiveDimensions(OrderFileAsset plate) {
        return plate.getDimensionXMm() != null && plate.getDimensionXMm().compareTo(BigDecimal.ZERO) > 0
                && plate.getDimensionYMm() != null && plate.getDimensionYMm().compareTo(BigDecimal.ZERO) > 0
                && plate.getDimensionZMm() != null && plate.getDimensionZMm().compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean isPlateMulticolor(OrderFileAsset plate) {
        return Boolean.TRUE.equals(plate.getIsMultiColor())
                || (plate.getColorCount() != null && plate.getColorCount() > 1);
    }

    private static int resolveRequiredColors(OrderFileAsset plate, boolean plateNeedsMulticolor) {
        if (!plateNeedsMulticolor) {
            return 1;
        }
        return plate.getColorCount() != null && plate.getColorCount() >= 2 ? plate.getColorCount() : 2;
    }

    private static int resolveCompanyMaxSlots(Company company, boolean companySupportsMulticolor) {
        if (!companySupportsMulticolor) {
            return 1;
        }
        return company.getMaxMulticolorSlots() != null && company.getMaxMulticolorSlots() >= 2
                ? company.getMaxMulticolorSlots()
                : 4;
    }

    private static int resolvePrinterSlots(Asset printer, PrinterCatalog catalog, int fallbackSlots) {
        if (printer.getMulticolorSlots() != null && printer.getMulticolorSlots() >= 2) {
            return printer.getMulticolorSlots();
        }
        if (catalog != null && catalog.getMaxColorSlots() != null && catalog.getMaxColorSlots() >= 2) {
            return catalog.getMaxColorSlots();
        }
        return Math.max(fallbackSlots, 4);
    }

    private static PrinterCatalog resolveCatalog(Asset asset, Map<Long, PrinterCatalog> catalogById) {
        if (asset == null || asset.getPrinterCatalogId() == null || catalogById == null) {
            return null;
        }
        return catalogById.get(asset.getPrinterCatalogId());
    }

    private static BigDecimal resolveDimension(BigDecimal primary, BigDecimal catalogFallback) {
        if (primary != null && primary.compareTo(BigDecimal.ZERO) > 0) {
            return primary;
        }
        if (catalogFallback != null && catalogFallback.compareTo(BigDecimal.ZERO) > 0) {
            return catalogFallback;
        }
        return null;
    }

    public static MaterialType parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if ("PLA+".equalsIgnoreCase(trimmed)) {
            return MaterialType.PLA;
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("RESINA".equals(normalized) || "SLA".equals(normalized)) {
            return MaterialType.RESIN;
        }
        if ("CF".equals(normalized) || "FIBRA_DE_CARBONO".equals(normalized)) {
            return MaterialType.CARBON_FIBER;
        }
        for (MaterialType mt : MaterialType.values()) {
            if (mt.name().equals(normalized) || mt.getCode().equalsIgnoreCase(normalized)) {
                return mt;
            }
        }
        return null;
    }

    public record PlateMatchSpec(
            int plateNumber,
            MaterialType material,
            BigDecimal dimensionXMm,
            BigDecimal dimensionYMm,
            BigDecimal dimensionZMm,
            boolean isMultiColor,
            int colorCount
    ) {
    }

    public record MatchedPlatePrinter(
            int plateNumber,
            @Nullable Long assetId,
            @Nullable String printerGuid,
            String printerName,
            String printerModel,
            PrinterSpeedTier speedTier,
            BigDecimal speedMultiplier,
            BigDecimal maxX,
            BigDecimal maxY,
            BigDecimal maxZ,
            PrinterStatus printerStatus
    ) {
    }

    public record InstantQuoteMatchResult(
            boolean eligible,
            @Nullable String rejectionReason,
            @Nullable MatchedPlatePrinter primaryPrinter,
            Map<Integer, MatchedPlatePrinter> plateMatches
    ) {
    }

    public InstantQuoteMatchResult matchPrintersForInstantQuote(
            Company company,
            @Nullable List<Asset> fleet,
            Map<Long, PrinterCatalog> catalogsById,
            List<PlateMatchSpec> plates,
            Set<MaterialType> pricedEnabledMaterials,
            PrinterSpeedResolver speedResolver
    ) {
        if (company == null || company.getStatus() != StatusCd.ACTIVE) {
            return new InstantQuoteMatchResult(false, "COMPANY_INACTIVE", null, Map.of());
        }
        if (company.getApprovalStatus() != null && company.getApprovalStatus() != MakerApprovalStatus.APPROVED) {
            return new InstantQuoteMatchResult(false, "MAKER_NOT_APPROVED", null, Map.of());
        }
        if (Boolean.TRUE.equals(company.getVacationMode())) {
            return new InstantQuoteMatchResult(false, "MAKER_ON_VACATION", null, Map.of());
        }
        if (Boolean.FALSE.equals(company.getAcceptingCustomQuotes())) {
            return new InstantQuoteMatchResult(false, "MAKER_NOT_ACCEPTING_QUOTES", null, Map.of());
        }
        if (plates == null || plates.isEmpty()) {
            return new InstantQuoteMatchResult(false, "NO_PLATES", null, Map.of());
        }
        if (pricedEnabledMaterials == null || pricedEnabledMaterials.isEmpty()) {
            return new InstantQuoteMatchResult(false, "NO_PRICED_MATERIALS", null, Map.of());
        }

        for (PlateMatchSpec plate : plates) {
            if (plate.material() == null || !pricedEnabledMaterials.contains(plate.material())) {
                return new InstantQuoteMatchResult(false, "MISSING_MATERIAL_PRICE", null, Map.of());
            }
        }

        Set<MaterialType> companyMaterials = new LinkedHashSet<>(
                jsonCodec.readEnumList(company.getMaterialsJson(), MaterialType.class)
        );
        List<Asset> activeOperationalPrinters = fleet == null ? List.of() : fleet.stream()
                .filter(Objects::nonNull)
                .filter(a -> a.getStatus() == StatusCd.ACTIVE)
                .filter(a -> a.getPrinterStatus() != PrinterStatus.OFFLINE
                        && a.getPrinterStatus() != PrinterStatus.MAINTENANCE
                        && a.getPrinterStatus() != PrinterStatus.ERROR)
                .toList();

        boolean hasAnyFleetAtAll = fleet != null && fleet.stream().anyMatch(a -> a != null && a.getStatus() == StatusCd.ACTIVE);
        if (!hasAnyFleetAtAll) {
            for (PlateMatchSpec plate : plates) {
                if (!fitsBoxWithRotation(plate.dimensionXMm(), plate.dimensionYMm(), plate.dimensionZMm(),
                        DEFAULT_BUILD_VOLUME_MM, DEFAULT_BUILD_VOLUME_MM, DEFAULT_BUILD_VOLUME_MM)) {
                    return new InstantQuoteMatchResult(false, "BUILD_VOLUME_EXCEEDED", null, Map.of());
                }
                if (!companyMaterials.isEmpty() && !companyMaterials.contains(plate.material())) {
                    return new InstantQuoteMatchResult(false, "MATERIAL_NOT_SUPPORTED", null, Map.of());
                }
                if (plate.isMultiColor()) {
                    if (!Boolean.TRUE.equals(company.getSupportsMulticolor())) {
                        return new InstantQuoteMatchResult(false, "MULTICOLOR_NOT_SUPPORTED", null, Map.of());
                    }
                    int companySlots = resolveCompanyMaxSlots(company, true);
                    if (Math.max(2, plate.colorCount()) > companySlots) {
                        return new InstantQuoteMatchResult(false, "COLOR_SLOTS_EXCEEDED", null, Map.of());
                    }
                }
            }
            Map<Integer, MatchedPlatePrinter> fallbackMap = new HashMap<>();
            String fallbackName = company.getPrimaryPrinter() != null && !company.getPrimaryPrinter().isBlank()
                    ? company.getPrimaryPrinter()
                    : "Impressora Padrão";
            for (PlateMatchSpec plate : plates) {
                fallbackMap.put(plate.plateNumber(), new MatchedPlatePrinter(
                        plate.plateNumber(),
                        null,
                        null,
                        fallbackName,
                        fallbackName,
                        PrinterSpeedTier.STANDARD,
                        PrinterSpeedTier.STANDARD.getDefaultMultiplier(),
                        DEFAULT_BUILD_VOLUME_MM,
                        DEFAULT_BUILD_VOLUME_MM,
                        DEFAULT_BUILD_VOLUME_MM,
                        PrinterStatus.ONLINE
                ));
            }
            return new InstantQuoteMatchResult(true, null, fallbackMap.get(plates.getFirst().plateNumber()), fallbackMap);
        }

        if (activeOperationalPrinters.isEmpty()) {
            return new InstantQuoteMatchResult(false, "NO_OPERATIONAL_PRINTERS", null, Map.of());
        }

        Map<Integer, MatchedPlatePrinter> matches = new HashMap<>();
        Comparator<MatchedPlatePrinter> printerRankComparator = Comparator
                .comparing(MatchedPlatePrinter::speedMultiplier)
                .thenComparingInt(p -> p.printerStatus() == PrinterStatus.ONLINE ? 0 : (p.printerStatus() == PrinterStatus.PRINTING ? 1 : 2))
                .thenComparing(p -> p.maxX().multiply(p.maxY()).multiply(p.maxZ()), Comparator.reverseOrder());

        for (PlateMatchSpec plate : plates) {
            List<MatchedPlatePrinter> candidates = new ArrayList<>();
            for (Asset asset : activeOperationalPrinters) {
                PrinterCatalog catalog = resolveCatalog(asset, catalogsById);
                BigDecimal maxX = resolveDimension(asset.getTrayWidth(), catalog != null ? catalog.getBuildVolumeXMm() : null);
                BigDecimal maxY = resolveDimension(asset.getTrayDepth(), catalog != null ? catalog.getBuildVolumeYMm() : null);
                BigDecimal maxZ = resolveDimension(asset.getTrayHeight(), catalog != null ? catalog.getBuildVolumeZMm() : null);
                if (maxX == null || maxY == null || maxZ == null) {
                    continue;
                }

                if (!fitsBoxWithRotation(plate.dimensionXMm(), plate.dimensionYMm(), plate.dimensionZMm(), maxX, maxY, maxZ)) {
                    continue;
                }

                Set<MaterialType> supportedMaterials = resolvePrinterSupportedMaterials(asset, catalog, companyMaterials);
                if (!supportedMaterials.isEmpty() && !supportedMaterials.contains(plate.material())) {
                    continue;
                }

                if (plate.isMultiColor()) {
                    if (!Boolean.TRUE.equals(asset.getSupportsMulticolor())) {
                        continue;
                    }
                    int effectiveSlots = resolvePrinterSlots(asset, catalog, 4);
                    if (Math.max(2, plate.colorCount()) > effectiveSlots) {
                        continue;
                    }
                }

                PrinterSpeedResolver.ResolvedPrinterSpeed resolvedSpeed = speedResolver.resolve(asset, catalog);
                String printerModel = catalog != null
                        ? catalog.getFullDisplayName()
                        : (asset.getModel() != null && !asset.getModel().isBlank() ? asset.getModel() : asset.getName());
                candidates.add(new MatchedPlatePrinter(
                        plate.plateNumber(),
                        asset.getId(),
                        asset.getGuid(),
                        asset.getName() != null && !asset.getName().isBlank() ? asset.getName() : printerModel,
                        printerModel,
                        resolvedSpeed.tier(),
                        resolvedSpeed.multiplier(),
                        maxX,
                        maxY,
                        maxZ,
                        asset.getPrinterStatus() != null ? asset.getPrinterStatus() : PrinterStatus.ONLINE
                ));
            }

            if (candidates.isEmpty()) {
                return new InstantQuoteMatchResult(false, "NO_COMPATIBLE_PRINTER_FOR_PLATE_" + plate.plateNumber(), null, Map.of());
            }

            candidates.sort(printerRankComparator);
            matches.put(plate.plateNumber(), candidates.getFirst());
        }

        MatchedPlatePrinter primary = matches.values().stream()
                .min(printerRankComparator)
                .orElse(null);
        return new InstantQuoteMatchResult(true, null, primary, matches);
    }
}
