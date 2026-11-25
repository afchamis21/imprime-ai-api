package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.admin.PatchFilamentCatalogRequest;
import org.imprime.ai.api.http.request.admin.UpsertFilamentCatalogRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.FilamentCatalogResponse;
import org.imprime.ai.api.model.FilamentCatalog;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.FilamentFinishType;
import org.imprime.ai.api.model.enums.HygroscopicLevel;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.SpoolType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.FilamentCatalogRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.FilamentCatalogService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.PageUtils;
import org.imprime.ai.api.util.SlugUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FilamentCatalogServiceImpl implements FilamentCatalogService {
    private static final String TARGET_TYPE_FILAMENT_CATALOG = "FILAMENT_CATALOG";
    private static final BigDecimal DEFAULT_DIAMETER_MM = new BigDecimal("1.75");
    private static final BigDecimal DEFAULT_DENSITY_G_CM3 = new BigDecimal("1.240");

    private final FilamentCatalogRepository filamentCatalogRepository;
    private final FilamentRepository filamentRepository;
    private final AdminAuditService adminAuditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FilamentCatalogResponse> listFilaments(
            String query,
            MaterialType material,
            String brand,
            FilamentFinishType finishType,
            Boolean amsCompatible,
            Boolean requiresEnclosure,
            Boolean requiresHardenedNozzle,
            HygroscopicLevel hygroscopicLevel,
            BigDecimal diameterMm,
            boolean includeInactive,
            Pageable pageable
    ) {
        List<FilamentCatalog> raw = includeInactive
                ? filamentCatalogRepository.findAllByOrderByBrandAscLineNameAscColorNameAsc()
                : filamentCatalogRepository.findAllByStatusOrderByBrandAscLineNameAscColorNameAsc(StatusCd.ACTIVE);

        String normalizedQuery = normalizeLower(query);
        String normalizedBrand = normalizeLower(brand);
        Map<Long, Long> inventoryCounts = loadInventoryCountsByCatalogId();
        Comparator<FilamentCatalog> comparator = resolveComparator(pageable);

        List<FilamentCatalogResponse> filtered = raw.stream()
                .filter(f -> includeInactive || StatusCd.ACTIVE.equals(f.getStatus()))
                .filter(f -> normalizedQuery == null || matchesQuery(f, normalizedQuery))
                .filter(f -> material == null || material.equals(f.getMaterial()))
                .filter(f -> normalizedBrand == null || (f.getBrand() != null && f.getBrand().toLowerCase(Locale.ROOT).contains(normalizedBrand)))
                .filter(f -> finishType == null || finishType.equals(f.getFinishType()))
                .filter(f -> amsCompatible == null || Objects.equals(amsCompatible, Boolean.TRUE.equals(f.getAmsCompatible())))
                .filter(f -> requiresEnclosure == null || Objects.equals(requiresEnclosure, Boolean.TRUE.equals(f.getRequiresEnclosure())))
                .filter(f -> requiresHardenedNozzle == null || Objects.equals(requiresHardenedNozzle, Boolean.TRUE.equals(f.getRequiresHardenedNozzle())))
                .filter(f -> hygroscopicLevel == null || hygroscopicLevel.equals(f.getHygroscopicLevel()))
                .filter(f -> diameterMm == null || (f.getDiameterMm() != null && f.getDiameterMm().compareTo(diameterMm) == 0))
                .sorted(comparator)
                .map(f -> toResponse(f, inventoryCounts.getOrDefault(f.getId(), 0L)))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public FilamentCatalogResponse getFilamentByGuid(String guid, boolean includeInactive) {
        FilamentCatalog entity = findFilamentOrThrow(guid, includeInactive);
        long count = filamentRepository.countByFilamentCatalogIdAndStatus(entity.getId(), StatusCd.ACTIVE);
        return toResponse(entity, count);
    }

    @Override
    @Transactional
    public FilamentCatalogResponse createFilament(UpsertFilamentCatalogRequest request) {
        String brand = request.brand().trim();
        String lineName = request.lineName().trim();
        String colorName = request.colorName().trim();
        BigDecimal diameterMm = request.diameterMm() != null ? request.diameterMm() : DEFAULT_DIAMETER_MM;

        if (filamentCatalogRepository.existsByBrandIgnoreCaseAndLineNameIgnoreCaseAndColorNameIgnoreCaseAndDiameterMmAndStatus(
                brand, lineName, colorName, diameterMm, StatusCd.ACTIVE)) {
            throw new ConflictException(MessageCd.FILAMENT_CATALOG_ALREADY_EXISTS);
        }

        FilamentCatalog entity = new FilamentCatalog();
        applyUpsertFields(entity, request, brand, lineName, colorName, diameterMm);
        entity.setSlug(SlugUtil.ensureUniqueSlug(
                brand + " " + lineName + " " + colorName,
                filamentCatalogRepository::existsBySlug
        ));

        FilamentCatalog saved = filamentCatalogRepository.save(entity);
        FilamentCatalogResponse after = toResponse(saved, 0L);

        adminAuditService.record(
                AuditAction.FILAMENT_CATALOG_CREATED,
                TARGET_TYPE_FILAMENT_CATALOG,
                saved.getGuid(),
                CatalogDtoMapper.firstNonBlank(request.reason(), "Cadastro de perfil de filamento no catálogo"),
                null,
                after
        );
        return after;
    }

    @Override
    @Transactional
    public FilamentCatalogResponse updateFilament(String guid, UpsertFilamentCatalogRequest request) {
        FilamentCatalog entity = findFilamentOrThrow(guid, true);
        long count = filamentRepository.countByFilamentCatalogIdAndStatus(entity.getId(), StatusCd.ACTIVE);
        FilamentCatalogResponse before = toResponse(entity, count);

        String brand = request.brand().trim();
        String lineName = request.lineName().trim();
        String colorName = request.colorName().trim();
        BigDecimal diameterMm = request.diameterMm() != null ? request.diameterMm() : DEFAULT_DIAMETER_MM;

        if (filamentCatalogRepository.existsByBrandIgnoreCaseAndLineNameIgnoreCaseAndColorNameIgnoreCaseAndDiameterMmAndStatusAndIdNot(
                brand, lineName, colorName, diameterMm, StatusCd.ACTIVE, entity.getId())) {
            throw new ConflictException(MessageCd.FILAMENT_CATALOG_ALREADY_EXISTS);
        }

        applyUpsertFields(entity, request, brand, lineName, colorName, diameterMm);
        FilamentCatalog saved = filamentCatalogRepository.save(entity);
        FilamentCatalogResponse after = toResponse(saved, count);

        adminAuditService.record(
                AuditAction.FILAMENT_CATALOG_UPDATED,
                TARGET_TYPE_FILAMENT_CATALOG,
                saved.getGuid(),
                CatalogDtoMapper.firstNonBlank(request.reason(), "Atualização completa de perfil de filamento"),
                before,
                after
        );
        return after;
    }

    @Override
    @Transactional
    public FilamentCatalogResponse patchFilament(String guid, PatchFilamentCatalogRequest request) {
        FilamentCatalog entity = findFilamentOrThrow(guid, true);
        long count = filamentRepository.countByFilamentCatalogIdAndStatus(entity.getId(), StatusCd.ACTIVE);
        FilamentCatalogResponse before = toResponse(entity, count);

        String brand = request.brand() != null ? request.brand().trim() : entity.getBrand();
        String lineName = request.lineName() != null ? request.lineName().trim() : entity.getLineName();
        String colorName = request.colorName() != null ? request.colorName().trim() : entity.getColorName();
        BigDecimal diameterMm = request.diameterMm() != null ? request.diameterMm() : entity.getDiameterMm();

        if (filamentCatalogRepository.existsByBrandIgnoreCaseAndLineNameIgnoreCaseAndColorNameIgnoreCaseAndDiameterMmAndStatusAndIdNot(
                brand, lineName, colorName, diameterMm, StatusCd.ACTIVE, entity.getId())) {
            throw new ConflictException(MessageCd.FILAMENT_CATALOG_ALREADY_EXISTS);
        }

        entity.setBrand(brand);
        entity.setLineName(lineName);
        entity.setColorName(colorName);
        entity.setDiameterMm(diameterMm);

        if (request.material() != null) {
            entity.setMaterial(request.material());
        }
        if (request.materialSubtype() != null) {
            entity.setMaterialSubtype(trimToNull(request.materialSubtype()));
        }
        if (request.colorHex() != null) {
            entity.setColorHex(normalizeHex(request.colorHex()));
        }
        if (request.secondaryColorHex() != null) {
            entity.setSecondaryColorHex(normalizeHexOrNull(request.secondaryColorHex()));
        }
        if (request.finishType() != null) {
            entity.setFinishType(request.finishType());
        }
        if (request.densityGCm3() != null) {
            entity.setDensityGCm3(request.densityGCm3());
        }
        if (request.defaultSpoolWeightG() != null) {
            entity.setDefaultSpoolWeightG(request.defaultSpoolWeightG());
        }
        if (request.spoolType() != null) {
            entity.setSpoolType(request.spoolType());
        }
        if (request.nozzleTempMinC() != null) {
            entity.setNozzleTempMinC(request.nozzleTempMinC());
        }
        if (request.nozzleTempMaxC() != null) {
            entity.setNozzleTempMaxC(request.nozzleTempMaxC());
        }
        if (request.nozzleTempRecommendedC() != null) {
            entity.setNozzleTempRecommendedC(request.nozzleTempRecommendedC());
        }
        if (request.bedTempMinC() != null) {
            entity.setBedTempMinC(request.bedTempMinC());
        }
        if (request.bedTempMaxC() != null) {
            entity.setBedTempMaxC(request.bedTempMaxC());
        }
        if (request.bedTempRecommendedC() != null) {
            entity.setBedTempRecommendedC(request.bedTempRecommendedC());
        }
        if (request.maxVolumetricSpeedMm3S() != null) {
            entity.setMaxVolumetricSpeedMm3S(request.maxVolumetricSpeedMm3S());
        }
        if (request.pressureAdvanceK() != null) {
            entity.setPressureAdvanceK(request.pressureAdvanceK());
        }
        if (request.transmissionDistanceMm() != null) {
            entity.setTransmissionDistanceMm(request.transmissionDistanceMm());
        }
        if (request.dryingTempC() != null) {
            entity.setDryingTempC(request.dryingTempC());
        }
        if (request.dryingTimeHours() != null) {
            entity.setDryingTimeHours(request.dryingTimeHours());
        }
        if (request.requiresEnclosure() != null) {
            entity.setRequiresEnclosure(request.requiresEnclosure());
        }
        if (request.requiresHardenedNozzle() != null) {
            entity.setRequiresHardenedNozzle(request.requiresHardenedNozzle());
        }
        if (request.amsCompatible() != null) {
            entity.setAmsCompatible(request.amsCompatible());
        }
        if (request.hygroscopicLevel() != null) {
            entity.setHygroscopicLevel(request.hygroscopicLevel());
        }
        if (request.shoreHardness() != null) {
            entity.setShoreHardness(trimToNull(request.shoreHardness()));
        }
        if (request.referencePriceBrlPerKg() != null) {
            entity.setReferencePriceBrlPerKg(request.referencePriceBrlPerKg());
        }
        if (request.sku() != null) {
            entity.setSku(trimToNull(request.sku()));
        }
        if (request.gtinEan() != null) {
            entity.setGtinEan(trimToNull(request.gtinEan()));
        }
        if (request.purchaseUrl() != null) {
            entity.setPurchaseUrl(trimToNull(request.purchaseUrl()));
        }
        if (request.imageUrl() != null) {
            entity.setImageUrl(trimToNull(request.imageUrl()));
        }
        if (request.notes() != null) {
            entity.setNotes(trimToNull(request.notes()));
        }
        if (request.status() != null && request.status() != StatusCd.DELETED) {
            entity.setStatus(request.status());
        }

        validateTemperatureRanges(entity);
        FilamentCatalog saved = filamentCatalogRepository.save(entity);
        FilamentCatalogResponse after = toResponse(saved, count);

        adminAuditService.record(
                AuditAction.FILAMENT_CATALOG_UPDATED,
                TARGET_TYPE_FILAMENT_CATALOG,
                saved.getGuid(),
                CatalogDtoMapper.firstNonBlank(request.reason(), "Edição rápida de perfil de filamento"),
                before,
                after
        );
        return after;
    }

    @Override
    @Transactional
    public void deleteFilament(String guid, String reason) {
        FilamentCatalog entity = findFilamentOrThrow(guid, true);
        long count = filamentRepository.countByFilamentCatalogIdAndStatus(entity.getId(), StatusCd.ACTIVE);
        FilamentCatalogResponse before = toResponse(entity, count);

        entity.setStatus(StatusCd.DELETED);
        filamentCatalogRepository.save(entity);

        adminAuditService.record(
                AuditAction.FILAMENT_CATALOG_DELETED,
                TARGET_TYPE_FILAMENT_CATALOG,
                entity.getGuid(),
                CatalogDtoMapper.firstNonBlank(reason, "Remoção lógica de perfil de filamento"),
                before,
                null
        );
    }

    private void applyUpsertFields(
            FilamentCatalog entity,
            UpsertFilamentCatalogRequest request,
            String brand,
            String lineName,
            String colorName,
            BigDecimal diameterMm
    ) {
        entity.setBrand(brand);
        entity.setLineName(lineName);
        entity.setMaterial(request.material());
        entity.setMaterialSubtype(trimToNull(request.materialSubtype()));
        entity.setColorName(colorName);
        entity.setColorHex(normalizeHex(request.colorHex()));
        entity.setSecondaryColorHex(normalizeHexOrNull(request.secondaryColorHex()));
        entity.setFinishType(request.finishType() != null ? request.finishType() : FilamentFinishType.STANDARD);
        entity.setDiameterMm(diameterMm);
        entity.setDensityGCm3(request.densityGCm3() != null ? request.densityGCm3() : DEFAULT_DENSITY_G_CM3);
        entity.setDefaultSpoolWeightG(request.defaultSpoolWeightG() != null ? request.defaultSpoolWeightG() : 1000);
        entity.setSpoolType(request.spoolType() != null ? request.spoolType() : SpoolType.PLASTIC);
        entity.setNozzleTempMinC(request.nozzleTempMinC());
        entity.setNozzleTempMaxC(request.nozzleTempMaxC());
        entity.setNozzleTempRecommendedC(request.nozzleTempRecommendedC());
        entity.setBedTempMinC(request.bedTempMinC());
        entity.setBedTempMaxC(request.bedTempMaxC());
        entity.setBedTempRecommendedC(request.bedTempRecommendedC());
        entity.setMaxVolumetricSpeedMm3S(request.maxVolumetricSpeedMm3S());
        entity.setPressureAdvanceK(request.pressureAdvanceK());
        entity.setTransmissionDistanceMm(request.transmissionDistanceMm());
        entity.setDryingTempC(request.dryingTempC());
        entity.setDryingTimeHours(request.dryingTimeHours());
        entity.setRequiresEnclosure(Boolean.TRUE.equals(request.requiresEnclosure()));
        entity.setRequiresHardenedNozzle(Boolean.TRUE.equals(request.requiresHardenedNozzle()));
        entity.setAmsCompatible(request.amsCompatible() == null || request.amsCompatible());
        entity.setHygroscopicLevel(request.hygroscopicLevel() != null ? request.hygroscopicLevel() : HygroscopicLevel.LOW);
        entity.setShoreHardness(trimToNull(request.shoreHardness()));
        entity.setReferencePriceBrlPerKg(request.referencePriceBrlPerKg());
        entity.setSku(trimToNull(request.sku()));
        entity.setGtinEan(trimToNull(request.gtinEan()));
        entity.setPurchaseUrl(trimToNull(request.purchaseUrl()));
        entity.setImageUrl(trimToNull(request.imageUrl()));
        entity.setNotes(trimToNull(request.notes()));
        entity.setStatus(StatusCd.ACTIVE);

        validateTemperatureRanges(entity);
    }

    private void validateTemperatureRanges(FilamentCatalog entity) {
        if (entity.getNozzleTempMinC() == null || entity.getNozzleTempMaxC() == null
                || entity.getBedTempMinC() == null || entity.getBedTempMaxC() == null) {
            throw new BadRequestException(MessageCd.INVALID_TEMPERATURE_RANGE);
        }
        if (entity.getNozzleTempMinC() > entity.getNozzleTempMaxC()
                || entity.getBedTempMinC() > entity.getBedTempMaxC()) {
            throw new BadRequestException(MessageCd.INVALID_TEMPERATURE_RANGE);
        }
        if (entity.getNozzleTempRecommendedC() != null
                && (entity.getNozzleTempRecommendedC() < entity.getNozzleTempMinC()
                || entity.getNozzleTempRecommendedC() > entity.getNozzleTempMaxC())) {
            throw new BadRequestException(MessageCd.INVALID_TEMPERATURE_RANGE);
        }
        if (entity.getBedTempRecommendedC() != null
                && (entity.getBedTempRecommendedC() < entity.getBedTempMinC()
                || entity.getBedTempRecommendedC() > entity.getBedTempMaxC())) {
            throw new BadRequestException(MessageCd.INVALID_TEMPERATURE_RANGE);
        }
    }

    private FilamentCatalog findFilamentOrThrow(String guid, boolean includeInactive) {
        FilamentCatalog entity = (includeInactive
                ? filamentCatalogRepository.findByGuid(guid)
                : filamentCatalogRepository.findByGuidAndStatus(guid, StatusCd.ACTIVE))
                .orElseThrow(() -> new NotFoundException(MessageCd.FILAMENT_CATALOG_NOT_FOUND));
        if (StatusCd.DELETED.equals(entity.getStatus())) {
            throw new NotFoundException(MessageCd.FILAMENT_CATALOG_NOT_FOUND);
        }
        return entity;
    }

    private Map<Long, Long> loadInventoryCountsByCatalogId() {
        Map<Long, Long> counts = new HashMap<>();
        List<Object[]> rows = filamentRepository.countGroupedByFilamentCatalogId(StatusCd.ACTIVE);
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

    private FilamentCatalogResponse toResponse(FilamentCatalog entity, long activeInventoryCount) {
        return new FilamentCatalogResponse(
                entity.getGuid(),
                entity.getSlug(),
                entity.getBrand(),
                entity.getLineName(),
                entity.getMaterial(),
                entity.getMaterialSubtype(),
                entity.getColorName(),
                entity.getColorHex(),
                entity.getSecondaryColorHex(),
                entity.getFinishType(),
                entity.getDiameterMm(),
                entity.getDensityGCm3(),
                entity.getDefaultSpoolWeightG(),
                entity.getSpoolType(),
                entity.getNozzleTempMinC(),
                entity.getNozzleTempMaxC(),
                entity.getNozzleTempRecommendedC(),
                entity.getBedTempMinC(),
                entity.getBedTempMaxC(),
                entity.getBedTempRecommendedC(),
                entity.getMaxVolumetricSpeedMm3S(),
                entity.getPressureAdvanceK(),
                entity.getTransmissionDistanceMm(),
                entity.getDryingTempC(),
                entity.getDryingTimeHours(),
                Boolean.TRUE.equals(entity.getRequiresEnclosure()),
                Boolean.TRUE.equals(entity.getRequiresHardenedNozzle()),
                Boolean.TRUE.equals(entity.getAmsCompatible()),
                entity.getHygroscopicLevel(),
                entity.getShoreHardness(),
                entity.getReferencePriceBrlPerKg(),
                entity.getSku(),
                entity.getGtinEan(),
                entity.getPurchaseUrl(),
                entity.getImageUrl(),
                entity.getNotes(),
                activeInventoryCount,
                entity.getStatus(),
                entity.getCreateDt(),
                entity.getUpdateDt()
        );
    }

    private Comparator<FilamentCatalog> resolveComparator(Pageable pageable) {
        Sort.Order order = pageable != null && pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next()
                : null;
        if (order == null) {
            return Comparator.comparing(FilamentCatalog::getBrand, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(FilamentCatalog::getLineName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(FilamentCatalog::getColorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }

        Comparator<FilamentCatalog> comparator = switch (order.getProperty()) {
            case "material" -> Comparator.comparing(f -> f.getMaterial() != null ? f.getMaterial().name() : "", String.CASE_INSENSITIVE_ORDER);
            case "price", "referencePriceBrlPerKg" -> Comparator.comparing(FilamentCatalog::getReferencePriceBrlPerKg, Comparator.nullsLast(Comparator.naturalOrder()));
            case "maxVolumetricSpeed", "maxVolumetricSpeedMm3S" -> Comparator.comparing(FilamentCatalog::getMaxVolumetricSpeedMm3S, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt", "createDt" -> Comparator.comparing(FilamentCatalog::getCreateDt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(FilamentCatalog::getBrand, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(FilamentCatalog::getLineName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(FilamentCatalog::getColorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };

        return order.isDescending() ? comparator.reversed() : comparator;
    }

    private boolean matchesQuery(FilamentCatalog f, String queryLower) {
        return containsIgnoreCase(f.getBrand(), queryLower)
                || containsIgnoreCase(f.getLineName(), queryLower)
                || containsIgnoreCase(f.getColorName(), queryLower)
                || containsIgnoreCase(f.getColorHex(), queryLower)
                || containsIgnoreCase(f.getSecondaryColorHex(), queryLower)
                || containsIgnoreCase(f.getMaterialSubtype(), queryLower)
                || containsIgnoreCase(f.getSku(), queryLower)
                || (f.getMaterial() != null && containsIgnoreCase(f.getMaterial().name(), queryLower));
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

    private static String normalizeHex(String hex) {
        return hex != null ? hex.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String normalizeHexOrNull(String hex) {
        if (hex == null) {
            return null;
        }
        String trimmed = hex.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
