package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.admin.PatchPrinterCatalogRequest;
import org.imprime.ai.api.http.request.admin.UpsertPrinterCatalogRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.PrinterCatalogResponse;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.PrinterConnectivity;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.PrinterCatalogService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.imprime.ai.api.util.SlugUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PrinterCatalogServiceImpl implements PrinterCatalogService {
    private static final String TARGET_TYPE_PRINTER_CATALOG = "PRINTER_CATALOG";
    private static final BigDecimal DEFAULT_NOZZLE_MM = new BigDecimal("0.400");
    private static final BigDecimal DEFAULT_FILAMENT_DIAMETER_MM = new BigDecimal("1.75");
    private static final String DEFAULT_INPUT_VOLTAGE = "BIVOLT_110_220V";

    private final PrinterCatalogRepository printerCatalogRepository;
    private final AssetRepository assetRepository;
    private final AdminAuditService adminAuditService;
    private final JsonCodec jsonCodec;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PrinterCatalogResponse> listPrinters(
            String query,
            String brand,
            PrinterTechnology technology,
            Boolean isEnclosed,
            Boolean supportsMulticolor,
            MaterialType material,
            BigDecimal minVolumeX,
            BigDecimal minVolumeY,
            BigDecimal minVolumeZ,
            boolean includeInactive,
            Pageable pageable
    ) {
        List<PrinterCatalog> raw = includeInactive
                ? printerCatalogRepository.findAllByOrderByBrandAscModelNameAsc()
                : printerCatalogRepository.findAllByStatusOrderByBrandAscModelNameAsc(StatusCd.ACTIVE);

        String normalizedQuery = normalizeLower(query);
        String normalizedBrand = normalizeLower(brand);
        Map<Long, Long> fleetCountByPrinterId = loadFleetCountsByCatalogId();

        Comparator<PrinterCatalog> comparator = resolveComparator(pageable);

        List<PrinterCatalogResponse> filtered = raw.stream()
                .filter(p -> includeInactive || StatusCd.ACTIVE.equals(p.getStatus()))
                .filter(p -> normalizedQuery == null || matchesQuery(p, normalizedQuery))
                .filter(p -> normalizedBrand == null || (p.getBrand() != null && p.getBrand().toLowerCase(Locale.ROOT).contains(normalizedBrand)))
                .filter(p -> technology == null || technology.equals(p.getTechnology()))
                .filter(p -> isEnclosed == null || Objects.equals(isEnclosed, Boolean.TRUE.equals(p.getIsEnclosed())))
                .filter(p -> supportsMulticolor == null || Objects.equals(supportsMulticolor, Boolean.TRUE.equals(p.getSupportsMulticolor())))
                .filter(p -> material == null || (p.getSupportedMaterials() != null && p.getSupportedMaterials().contains(material)))
                .filter(p -> minVolumeX == null || (p.getBuildVolumeXMm() != null && p.getBuildVolumeXMm().compareTo(minVolumeX) >= 0))
                .filter(p -> minVolumeY == null || (p.getBuildVolumeYMm() != null && p.getBuildVolumeYMm().compareTo(minVolumeY) >= 0))
                .filter(p -> minVolumeZ == null || (p.getBuildVolumeZMm() != null && p.getBuildVolumeZMm().compareTo(minVolumeZ) >= 0))
                .sorted(comparator)
                .map(p -> toResponse(p, fleetCountByPrinterId.getOrDefault(p.getId(), 0L)))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PrinterCatalogResponse getPrinterByGuid(String guid, boolean includeInactive) {
        PrinterCatalog entity = findPrinterOrThrow(guid, includeInactive);
        long fleetCount = assetRepository.countByPrinterCatalogIdAndStatus(entity.getId(), StatusCd.ACTIVE);
        return toResponse(entity, fleetCount);
    }

    @Override
    @Transactional
    public PrinterCatalogResponse createPrinter(UpsertPrinterCatalogRequest request) {
        String brand = request.brand().trim();
        String modelName = request.modelName().trim();

        if (printerCatalogRepository.existsByBrandIgnoreCaseAndModelNameIgnoreCaseAndStatus(brand, modelName, StatusCd.ACTIVE)) {
            throw new ConflictException(MessageCd.PRINTER_CATALOG_ALREADY_EXISTS);
        }

        PrinterCatalog entity = new PrinterCatalog();
        applyUpsertFields(entity, request, brand, modelName);
        entity.setSlug(SlugUtil.ensureUniqueSlug(brand + " " + modelName, printerCatalogRepository::existsBySlug));

        PrinterCatalog saved = printerCatalogRepository.save(entity);
        PrinterCatalogResponse after = toResponse(saved, 0L);

        adminAuditService.record(
                AuditAction.PRINTER_CATALOG_CREATED,
                TARGET_TYPE_PRINTER_CATALOG,
                saved.getGuid(),
                CatalogDtoMapper.firstNonBlank(request.reason(), "Cadastro de impressora 3D no catálogo"),
                null,
                after
        );
        return after;
    }

    @Override
    @Transactional
    public PrinterCatalogResponse updatePrinter(String guid, UpsertPrinterCatalogRequest request) {
        PrinterCatalog entity = findPrinterOrThrow(guid, true);
        long fleetCount = assetRepository.countByPrinterCatalogIdAndStatus(entity.getId(), StatusCd.ACTIVE);
        PrinterCatalogResponse before = toResponse(entity, fleetCount);

        String brand = request.brand().trim();
        String modelName = request.modelName().trim();

        if (printerCatalogRepository.existsByBrandIgnoreCaseAndModelNameIgnoreCaseAndStatusAndIdNot(
                brand, modelName, StatusCd.ACTIVE, entity.getId())) {
            throw new ConflictException(MessageCd.PRINTER_CATALOG_ALREADY_EXISTS);
        }

        applyUpsertFields(entity, request, brand, modelName);
        PrinterCatalog saved = printerCatalogRepository.save(entity);
        PrinterCatalogResponse after = toResponse(saved, fleetCount);

        adminAuditService.record(
                AuditAction.PRINTER_CATALOG_UPDATED,
                TARGET_TYPE_PRINTER_CATALOG,
                saved.getGuid(),
                CatalogDtoMapper.firstNonBlank(request.reason(), "Atualização completa de impressora 3D no catálogo"),
                before,
                after
        );
        return after;
    }

    @Override
    @Transactional
    public PrinterCatalogResponse patchPrinter(String guid, PatchPrinterCatalogRequest request) {
        PrinterCatalog entity = findPrinterOrThrow(guid, true);
        long fleetCount = assetRepository.countByPrinterCatalogIdAndStatus(entity.getId(), StatusCd.ACTIVE);
        PrinterCatalogResponse before = toResponse(entity, fleetCount);

        String brand = request.brand() != null ? request.brand().trim() : entity.getBrand();
        String modelName = request.modelName() != null ? request.modelName().trim() : entity.getModelName();

        if (printerCatalogRepository.existsByBrandIgnoreCaseAndModelNameIgnoreCaseAndStatusAndIdNot(
                brand, modelName, StatusCd.ACTIVE, entity.getId())) {
            throw new ConflictException(MessageCd.PRINTER_CATALOG_ALREADY_EXISTS);
        }

        entity.setBrand(brand);
        entity.setModelName(modelName);
        if (request.buildVolumeXMm() != null) {
            entity.setBuildVolumeXMm(request.buildVolumeXMm());
        }
        if (request.buildVolumeYMm() != null) {
            entity.setBuildVolumeYMm(request.buildVolumeYMm());
        }
        if (request.buildVolumeZMm() != null) {
            entity.setBuildVolumeZMm(request.buildVolumeZMm());
        }
        if (request.isEnclosed() != null) {
            entity.setIsEnclosed(request.isEnclosed());
        }
        if (request.maxNozzleTempC() != null) {
            entity.setMaxNozzleTempC(request.maxNozzleTempC());
        }
        if (request.maxBedTempC() != null) {
            entity.setMaxBedTempC(request.maxBedTempC());
        }
        if (request.defaultNozzleMm() != null) {
            entity.setDefaultNozzleMm(request.defaultNozzleMm());
        }
        if (request.filamentDiameterMm() != null) {
            entity.setFilamentDiameterMm(request.filamentDiameterMm());
        }
        if (request.maxPrintSpeedMmS() != null) {
            entity.setMaxPrintSpeedMmS(request.maxPrintSpeedMmS());
        }
        if (request.maxPowerWatts110v() != null) {
            entity.setMaxPowerWatts110v(request.maxPowerWatts110v());
        }
        if (request.maxPowerWatts220v() != null) {
            entity.setMaxPowerWatts220v(request.maxPowerWatts220v());
        }
        if (request.inputVoltage() != null && !request.inputVoltage().isBlank()) {
            entity.setInputVoltage(request.inputVoltage().trim());
        }
        if (request.connectivityMethods() != null) {
            entity.setConnectivityMethodsJson(jsonCodec.toEnumArrayJson(request.connectivityMethods()));
        }
        if (request.weightKg() != null) {
            entity.setWeightKg(request.weightKg());
        }
        if (request.supportedMaterials() != null) {
            entity.setSupportedMaterials(new LinkedHashSet<>(request.supportedMaterials()));
        }
        if (request.technology() != null) {
            entity.setTechnology(request.technology());
        }
        if (request.kinematics() != null) {
            entity.setKinematics(request.kinematics());
        }
        if (request.extruderType() != null) {
            entity.setExtruderType(request.extruderType());
        }
        if (request.supportsMulticolor() != null) {
            entity.setSupportsMulticolor(request.supportsMulticolor());
        }
        if (request.multicolorSystemName() != null) {
            entity.setMulticolorSystemName(trimToNull(request.multicolorSystemName()));
        }
        if (request.maxColorSlots() != null) {
            entity.setMaxColorSlots(request.maxColorSlots());
        }
        if (Boolean.FALSE.equals(entity.getSupportsMulticolor())) {
            entity.setMaxColorSlots(1);
        } else if (entity.getMaxColorSlots() == null || entity.getMaxColorSlots() < 2) {
            entity.setMaxColorSlots(4);
        }
        if (request.avgPowerWatts() != null) {
            entity.setAvgPowerWatts(request.avgPowerWatts());
        }
        if (request.activeChamberHeating() != null) {
            entity.setActiveChamberHeating(request.activeChamberHeating());
        }
        if (request.maxChamberTempC() != null) {
            entity.setMaxChamberTempC(request.maxChamberTempC());
        }
        if (request.minLayerHeightMm() != null) {
            entity.setMinLayerHeightMm(request.minLayerHeightMm());
        }
        if (request.maxLayerHeightMm() != null) {
            entity.setMaxLayerHeightMm(request.maxLayerHeightMm());
        }
        if (request.maxAccelerationMmS2() != null) {
            entity.setMaxAccelerationMmS2(request.maxAccelerationMmS2());
        }
        if (request.supportedNozzleSizesMm() != null) {
            entity.setSupportedNozzleSizesJson(encodeNozzleSizes(request.supportedNozzleSizesMm(), entity.getDefaultNozzleMm()));
        }
        if (request.hardenedNozzleDefault() != null) {
            entity.setHardenedNozzleDefault(request.hardenedNozzleDefault());
        }
        if (request.hasAutoBedLeveling() != null) {
            entity.setHasAutoBedLeveling(request.hasAutoBedLeveling());
        }
        if (request.hasFilamentRunoutSensor() != null) {
            entity.setHasFilamentRunoutSensor(request.hasFilamentRunoutSensor());
        }
        if (request.hasCamera() != null) {
            entity.setHasCamera(request.hasCamera());
        }
        if (request.hasAirFiltration() != null) {
            entity.setHasAirFiltration(request.hasAirFiltration());
        }
        if (request.releaseYear() != null) {
            entity.setReleaseYear(request.releaseYear());
        }
        if (request.estimatedPriceBrl() != null) {
            entity.setEstimatedPriceBrl(request.estimatedPriceBrl());
        }
        if (request.imageUrl() != null) {
            entity.setImageUrl(trimToNull(request.imageUrl()));
        }
        if (request.officialSpecsUrl() != null) {
            entity.setOfficialSpecsUrl(trimToNull(request.officialSpecsUrl()));
        }
        if (request.notes() != null) {
            entity.setNotes(trimToNull(request.notes()));
        }
        if (request.status() != null && request.status() != StatusCd.DELETED) {
            entity.setStatus(request.status());
        }

        validatePrinterConstraints(entity);
        PrinterCatalog saved = printerCatalogRepository.save(entity);
        PrinterCatalogResponse after = toResponse(saved, fleetCount);

        adminAuditService.record(
                AuditAction.PRINTER_CATALOG_UPDATED,
                TARGET_TYPE_PRINTER_CATALOG,
                saved.getGuid(),
                CatalogDtoMapper.firstNonBlank(request.reason(), "Edição rápida de impressora 3D no catálogo"),
                before,
                after
        );
        return after;
    }

    @Override
    @Transactional
    public void deletePrinter(String guid, String reason) {
        PrinterCatalog entity = findPrinterOrThrow(guid, true);
        long fleetCount = assetRepository.countByPrinterCatalogIdAndStatus(entity.getId(), StatusCd.ACTIVE);
        PrinterCatalogResponse before = toResponse(entity, fleetCount);

        entity.setStatus(StatusCd.DELETED);
        printerCatalogRepository.save(entity);

        adminAuditService.record(
                AuditAction.PRINTER_CATALOG_DELETED,
                TARGET_TYPE_PRINTER_CATALOG,
                entity.getGuid(),
                CatalogDtoMapper.firstNonBlank(reason, "Remoção lógica de impressora 3D do catálogo"),
                before,
                null
        );
    }

    private void applyUpsertFields(
            PrinterCatalog entity,
            UpsertPrinterCatalogRequest request,
            String brand,
            String modelName
    ) {
        entity.setBrand(brand);
        entity.setModelName(modelName);
        entity.setBuildVolumeXMm(request.buildVolumeXMm());
        entity.setBuildVolumeYMm(request.buildVolumeYMm());
        entity.setBuildVolumeZMm(request.buildVolumeZMm());
        entity.setIsEnclosed(Boolean.TRUE.equals(request.isEnclosed()));
        entity.setMaxNozzleTempC(request.maxNozzleTempC());
        entity.setMaxBedTempC(request.maxBedTempC());
        entity.setDefaultNozzleMm(request.defaultNozzleMm() != null ? request.defaultNozzleMm() : DEFAULT_NOZZLE_MM);
        entity.setFilamentDiameterMm(request.filamentDiameterMm() != null ? request.filamentDiameterMm() : DEFAULT_FILAMENT_DIAMETER_MM);
        entity.setMaxPrintSpeedMmS(request.maxPrintSpeedMmS());
        entity.setMaxPowerWatts110v(request.maxPowerWatts110v());
        entity.setMaxPowerWatts220v(request.maxPowerWatts220v());
        entity.setInputVoltage(CatalogDtoMapper.firstNonBlank(request.inputVoltage(), DEFAULT_INPUT_VOLTAGE));
        entity.setConnectivityMethodsJson(jsonCodec.toEnumArrayJson(request.connectivityMethods()));
        entity.setWeightKg(request.weightKg());
        entity.setSupportedMaterials(request.supportedMaterials() != null
                ? new LinkedHashSet<>(request.supportedMaterials())
                : new LinkedHashSet<>());
        entity.setTechnology(request.technology() != null ? request.technology() : PrinterTechnology.FDM);
        entity.setKinematics(request.kinematics());
        entity.setExtruderType(request.extruderType());

        boolean multicolor = Boolean.TRUE.equals(request.supportsMulticolor());
        entity.setSupportsMulticolor(multicolor);
        entity.setMulticolorSystemName(trimToNull(request.multicolorSystemName()));
        int slots = request.maxColorSlots() != null ? request.maxColorSlots() : (multicolor ? 4 : 1);
        entity.setMaxColorSlots(multicolor ? Math.max(slots, 2) : 1);

        entity.setAvgPowerWatts(request.avgPowerWatts());
        entity.setActiveChamberHeating(Boolean.TRUE.equals(request.activeChamberHeating()));
        entity.setMaxChamberTempC(request.maxChamberTempC());
        entity.setMinLayerHeightMm(request.minLayerHeightMm());
        entity.setMaxLayerHeightMm(request.maxLayerHeightMm());
        entity.setMaxAccelerationMmS2(request.maxAccelerationMmS2());
        entity.setSupportedNozzleSizesJson(encodeNozzleSizes(request.supportedNozzleSizesMm(), entity.getDefaultNozzleMm()));
        entity.setHardenedNozzleDefault(Boolean.TRUE.equals(request.hardenedNozzleDefault()));
        entity.setHasAutoBedLeveling(request.hasAutoBedLeveling() == null || request.hasAutoBedLeveling());
        entity.setHasFilamentRunoutSensor(request.hasFilamentRunoutSensor() == null || request.hasFilamentRunoutSensor());
        entity.setHasCamera(Boolean.TRUE.equals(request.hasCamera()));
        entity.setHasAirFiltration(Boolean.TRUE.equals(request.hasAirFiltration()));
        entity.setReleaseYear(request.releaseYear());
        entity.setEstimatedPriceBrl(request.estimatedPriceBrl());
        entity.setImageUrl(trimToNull(request.imageUrl()));
        entity.setOfficialSpecsUrl(trimToNull(request.officialSpecsUrl()));
        entity.setNotes(trimToNull(request.notes()));
        entity.setStatus(StatusCd.ACTIVE);

        validatePrinterConstraints(entity);
    }

    private void validatePrinterConstraints(PrinterCatalog entity) {
        if ((entity.getMaxNozzleTempC() != null && entity.getMaxNozzleTempC() < 0)
                || (entity.getMaxBedTempC() != null && entity.getMaxBedTempC() < 0)) {
            throw new BadRequestException(MessageCd.INVALID_TEMPERATURE_RANGE);
        }
        if (entity.getMinLayerHeightMm() != null && entity.getMaxLayerHeightMm() != null
                && entity.getMinLayerHeightMm().compareTo(entity.getMaxLayerHeightMm()) > 0) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
    }

    private PrinterCatalog findPrinterOrThrow(String guid, boolean includeInactive) {
        PrinterCatalog entity = (includeInactive
                ? printerCatalogRepository.findByGuid(guid)
                : printerCatalogRepository.findByGuidAndStatus(guid, StatusCd.ACTIVE))
                .orElseThrow(() -> new NotFoundException(MessageCd.PRINTER_CATALOG_NOT_FOUND));
        if (StatusCd.DELETED.equals(entity.getStatus())) {
            throw new NotFoundException(MessageCd.PRINTER_CATALOG_NOT_FOUND);
        }
        return entity;
    }

    private Map<Long, Long> loadFleetCountsByCatalogId() {
        Map<Long, Long> counts = new HashMap<>();
        List<Object[]> rows = assetRepository.countGroupedByPrinterCatalogId(StatusCd.ACTIVE);
        if (rows == null) {
            return counts;
        }
        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] instanceof Number id && row[1] instanceof Number cnt) {
                counts.put(id.longValue(), cnt.longValue());
            }
        }
        return counts;
    }

    private String encodeNozzleSizes(List<BigDecimal> sizes, BigDecimal fallbackNozzle) {
        List<BigDecimal> resolved = (sizes == null || sizes.isEmpty())
                ? List.of(fallbackNozzle != null ? fallbackNozzle : DEFAULT_NOZZLE_MM)
                : sizes.stream().filter(Objects::nonNull).distinct().sorted().toList();
        return jsonCodec.toJson(resolved);
    }

    private PrinterCatalogResponse toResponse(PrinterCatalog entity, long activeFleetCount) {
        List<PrinterConnectivity> connectivity = jsonCodec.readEnumList(
                entity.getConnectivityMethodsJson(), PrinterConnectivity.class);
        List<BigDecimal> nozzleSizes = jsonCodec.readList(
                entity.getSupportedNozzleSizesJson(), BigDecimal.class);
        if (nozzleSizes == null || nozzleSizes.isEmpty()) {
            nozzleSizes = entity.getDefaultNozzleMm() != null
                    ? List.of(entity.getDefaultNozzleMm())
                    : List.of(DEFAULT_NOZZLE_MM);
        }
        List<MaterialType> materials = entity.getSupportedMaterials() == null
                ? List.of()
                : entity.getSupportedMaterials().stream().sorted(Comparator.comparing(Enum::name)).toList();

        return new PrinterCatalogResponse(
                entity.getGuid(),
                entity.getSlug(),
                entity.getBrand(),
                entity.getModelName(),
                entity.getFullDisplayName(),
                entity.getBuildVolumeXMm(),
                entity.getBuildVolumeYMm(),
                entity.getBuildVolumeZMm(),
                Boolean.TRUE.equals(entity.getIsEnclosed()),
                entity.getMaxNozzleTempC(),
                entity.getMaxBedTempC(),
                entity.getDefaultNozzleMm(),
                entity.getFilamentDiameterMm(),
                entity.getMaxPrintSpeedMmS(),
                entity.getMaxPowerWatts110v(),
                entity.getMaxPowerWatts220v(),
                entity.getInputVoltage(),
                connectivity,
                entity.getWeightKg(),
                entity.getTechnology(),
                entity.getKinematics(),
                entity.getExtruderType(),
                Boolean.TRUE.equals(entity.getSupportsMulticolor()),
                entity.getMulticolorSystemName(),
                entity.getMaxColorSlots() != null ? entity.getMaxColorSlots() : 1,
                entity.getAvgPowerWatts(),
                Boolean.TRUE.equals(entity.getActiveChamberHeating()),
                entity.getMaxChamberTempC(),
                entity.getMinLayerHeightMm(),
                entity.getMaxLayerHeightMm(),
                entity.getMaxAccelerationMmS2(),
                nozzleSizes,
                Boolean.TRUE.equals(entity.getHardenedNozzleDefault()),
                Boolean.TRUE.equals(entity.getHasAutoBedLeveling()),
                Boolean.TRUE.equals(entity.getHasFilamentRunoutSensor()),
                Boolean.TRUE.equals(entity.getHasCamera()),
                Boolean.TRUE.equals(entity.getHasAirFiltration()),
                entity.getReleaseYear(),
                entity.getEstimatedPriceBrl(),
                entity.getImageUrl(),
                entity.getOfficialSpecsUrl(),
                entity.getNotes(),
                materials,
                activeFleetCount,
                entity.getStatus(),
                entity.getCreateDt(),
                entity.getUpdateDt()
        );
    }

    private Comparator<PrinterCatalog> resolveComparator(Pageable pageable) {
        Sort.Order order = pageable != null && pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next()
                : null;
        if (order == null) {
            return Comparator.comparing(PrinterCatalog::getBrand, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(PrinterCatalog::getModelName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }

        Comparator<PrinterCatalog> comparator = switch (order.getProperty()) {
            case "modelName" -> Comparator.comparing(PrinterCatalog::getModelName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "buildVolume" -> Comparator.comparing(this::calculateBuildVolumeCm3, Comparator.nullsLast(Comparator.naturalOrder()));
            case "maxPrintSpeedMmS", "maxPrintSpeed" -> Comparator.comparing(PrinterCatalog::getMaxPrintSpeedMmS, Comparator.nullsLast(Comparator.naturalOrder()));
            case "estimatedPriceBrl", "estimatedPrice" -> Comparator.comparing(PrinterCatalog::getEstimatedPriceBrl, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt", "createDt" -> Comparator.comparing(PrinterCatalog::getCreateDt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(PrinterCatalog::getBrand, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(PrinterCatalog::getModelName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };

        return order.isDescending() ? comparator.reversed() : comparator;
    }

    private BigDecimal calculateBuildVolumeCm3(PrinterCatalog p) {
        if (p.getBuildVolumeXMm() == null || p.getBuildVolumeYMm() == null || p.getBuildVolumeZMm() == null) {
            return BigDecimal.ZERO;
        }
        return p.getBuildVolumeXMm().multiply(p.getBuildVolumeYMm()).multiply(p.getBuildVolumeZMm());
    }

    private boolean matchesQuery(PrinterCatalog p, String queryLower) {
        return containsIgnoreCase(p.getBrand(), queryLower)
                || containsIgnoreCase(p.getModelName(), queryLower)
                || containsIgnoreCase(p.getFullDisplayName(), queryLower)
                || containsIgnoreCase(p.getMulticolorSystemName(), queryLower)
                || containsIgnoreCase(p.getSlug(), queryLower);
    }

    private static boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }

    private static String normalizeLower(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
