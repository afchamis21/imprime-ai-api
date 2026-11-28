package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.UpdateMakerMarketplacePricingRequest;
import org.imprime.ai.api.http.response.maker.MakerMarketplacePricingResponse;
import org.imprime.ai.api.http.response.maker.MakerMarketplacePricingResponse.MaterialPriceItem;
import org.imprime.ai.api.http.response.maker.MakerMarketplacePricingResponse.PrinterSpeedPreview;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerMaterialPrice;
import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.SetupFeeScope;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerMaterialPriceRepository;
import org.imprime.ai.api.repo.db.MakerPricingProfileRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerMarketplacePricingService;
import org.imprime.ai.api.service.PrinterSpeedResolver;
import org.imprime.ai.api.service.PrinterSpeedResolver.ResolvedPrinterSpeed;
import org.imprime.ai.api.util.JsonCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerMarketplacePricingServiceImpl implements MakerMarketplacePricingService {

    private static final Map<MaterialType, BigDecimal> DEFAULT_MATERIAL_PRICES = createDefaultPrices();

    private final MakerCompanyResolver makerCompanyResolver;
    private final MakerPricingProfileRepository pricingProfileRepository;
    private final MakerMaterialPriceRepository materialPriceRepository;
    private final AssetRepository assetRepository;
    private final PrinterCatalogRepository printerCatalogRepository;
    private final CompanyRepository companyRepository;
    private final PrinterSpeedResolver printerSpeedResolver;
    private final JsonCodec jsonCodec;

    @Override
    @Transactional(readOnly = true)
    public MakerMarketplacePricingResponse getMyPricing() {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerPricingProfile profile = pricingProfileRepository
                .findByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE)
                .orElseGet(() -> buildFallbackProfile(company));

        List<MakerMaterialPrice> persistedPrices = materialPriceRepository
                .findAllByCompanyIdAndStatusOrderByMaterialAsc(company.getId(), StatusCd.ACTIVE);

        List<MaterialPriceItem> materialItems = persistedPrices.isEmpty()
                ? deriveInitialMaterialsFromCompany(company)
                : persistedPrices.stream()
                .map(p -> new MaterialPriceItem(
                        p.getMaterial(),
                        p.getPricePerGramBrl(),
                        Boolean.TRUE.equals(p.getEnabled()),
                        p.getNotes()
                ))
                .toList();

        return toResponse(company, profile, materialItems);
    }

    @Override
    @Transactional
    public MakerMarketplacePricingResponse updateMyPricing(UpdateMakerMarketplacePricingRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerPricingProfile profile = pricingProfileRepository
                .findByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE)
                .orElseGet(() -> MakerPricingProfile.defaultForCompany(company.getId()));

        profile.setInstantQuoteEnabled(request.instantQuoteEnabled() != null ? request.instantQuoteEnabled() : Boolean.TRUE);
        profile.setHourlyRateBrl(scale2(request.hourlyRateBrl()));
        profile.setMinimumOrderFeeBrl(scale2(request.minimumOrderFeeBrl()));
        profile.setSetupFeeBrl(scale2(request.setupFeeBrl()));
        profile.setSetupFeeScope(request.setupFeeScope() != null ? request.setupFeeScope() : SetupFeeScope.PER_PLATE);
        profile.setSupportRemovalFeeBrl(scale2(request.supportRemovalFeeBrl()));
        profile.setSupportPricePerGramBrl(scale4(request.supportPricePerGramBrl()));
        profile.setLeadTimeDays(request.leadTimeDays());
        profile.setExpressMultiplier(scale2OrDefault(request.expressMultiplier(), new BigDecimal("1.20")));
        profile.setFinishSandedMultiplier(scale2OrDefault(request.finishSandedMultiplier(), new BigDecimal("1.15")));
        profile.setFinishVaporMultiplier(scale2OrDefault(request.finishVaporMultiplier(), new BigDecimal("1.25")));
        profile.setFinishPaintedMultiplier(scale2OrDefault(request.finishPaintedMultiplier(), new BigDecimal("1.35")));
        profile.setStatus(StatusCd.ACTIVE);
        MakerPricingProfile savedProfile = pricingProfileRepository.save(profile);

        List<MakerMaterialPrice> existing = materialPriceRepository
                .findAllByCompanyIdAndStatusOrderByMaterialAsc(company.getId(), StatusCd.ACTIVE);
        Map<MaterialType, MakerMaterialPrice> existingByMat = new EnumMap<>(MaterialType.class);
        for (MakerMaterialPrice item : existing) {
            existingByMat.put(item.getMaterial(), item);
        }

        Map<MaterialType, UpdateMakerMarketplacePricingRequest.MaterialPriceInput> requestedByMat = new LinkedHashMap<>();
        for (var input : request.materialPrices()) {
            requestedByMat.put(input.material(), input);
        }

        List<MakerMaterialPrice> toSave = new ArrayList<>();
        for (var input : requestedByMat.values()) {
            MakerMaterialPrice row = existingByMat.getOrDefault(input.material(), new MakerMaterialPrice());
            row.setCompanyId(company.getId());
            row.setMaterial(input.material());
            row.setPricePerGramBrl(scale4(input.pricePerGramBrl()));
            row.setEnabled(input.enabled() == null || input.enabled());
            row.setNotes(trimToNull(input.notes()));
            row.setStatus(StatusCd.ACTIVE);
            toSave.add(row);
        }

        for (MakerMaterialPrice row : existing) {
            if (!requestedByMat.containsKey(row.getMaterial())) {
                row.setEnabled(Boolean.FALSE);
                toSave.add(row);
            }
        }

        List<MakerMaterialPrice> savedMaterials = materialPriceRepository.saveAll(toSave).stream()
                .sorted(Comparator.comparing(m -> m.getMaterial().name()))
                .toList();

        syncCompanySummaryFields(company, savedProfile, savedMaterials);
        companyRepository.save(company);

        List<MaterialPriceItem> items = savedMaterials.stream()
                .map(p -> new MaterialPriceItem(
                        p.getMaterial(),
                        p.getPricePerGramBrl(),
                        Boolean.TRUE.equals(p.getEnabled()),
                        p.getNotes()
                ))
                .toList();

        return toResponse(company, savedProfile, items);
    }

    private MakerMarketplacePricingResponse toResponse(
            Company company,
            MakerPricingProfile profile,
            List<MaterialPriceItem> materialItems
    ) {
        List<Asset> printers = assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE);
        List<Long> catalogIds = printers.stream()
                .map(Asset::getPrinterCatalogId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, PrinterCatalog> catalogsById = catalogIds.isEmpty()
                ? Map.of()
                : printerCatalogRepository.findAllById(catalogIds).stream()
                .collect(Collectors.toMap(PrinterCatalog::getId, Function.identity()));

        List<PrinterSpeedPreview> previews = printers.stream()
                .map(asset -> {
                    PrinterCatalog catalog = asset.getPrinterCatalogId() != null
                            ? catalogsById.get(asset.getPrinterCatalogId())
                            : null;
                    ResolvedPrinterSpeed resolved = printerSpeedResolver.resolve(asset, catalog);
                    return new PrinterSpeedPreview(
                            asset.getGuid(),
                            asset.getName(),
                            asset.getModel(),
                            catalog != null ? catalog.getGuid() : null,
                            resolved.tier(),
                            resolved.multiplier(),
                            resolved.source(),
                            resolveDim(asset.getTrayWidth(), catalog != null ? catalog.getBuildVolumeXMm() : null),
                            resolveDim(asset.getTrayDepth(), catalog != null ? catalog.getBuildVolumeYMm() : null),
                            resolveDim(asset.getTrayHeight(), catalog != null ? catalog.getBuildVolumeZMm() : null)
                    );
                })
                .toList();

        return new MakerMarketplacePricingResponse(
                Boolean.TRUE.equals(profile.getInstantQuoteEnabled()),
                profile.getHourlyRateBrl(),
                profile.getMinimumOrderFeeBrl(),
                profile.getSetupFeeBrl(),
                profile.getSetupFeeScope(),
                profile.getSupportRemovalFeeBrl(),
                profile.getSupportPricePerGramBrl(),
                profile.getLeadTimeDays(),
                profile.getExpressMultiplier(),
                profile.getFinishSandedMultiplier(),
                profile.getFinishVaporMultiplier(),
                profile.getFinishPaintedMultiplier(),
                materialItems,
                previews
        );
    }

    private void syncCompanySummaryFields(
            Company company,
            MakerPricingProfile profile,
            List<MakerMaterialPrice> savedMaterials
    ) {
        company.setMinOrderValue(profile.getMinimumOrderFeeBrl());
        company.setMinLeadTimeDays(profile.getLeadTimeDays());
        if (company.getMaxLeadTimeDays() == null || company.getMaxLeadTimeDays() < profile.getLeadTimeDays()) {
            company.setMaxLeadTimeDays(profile.getLeadTimeDays());
        }
        List<String> enabledMaterials = savedMaterials.stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled()))
                .map(m -> m.getMaterial().name())
                .toList();
        if (!enabledMaterials.isEmpty()) {
            company.setMaterialsJson(jsonCodec.toJson(enabledMaterials));
        }
    }

    private MakerPricingProfile buildFallbackProfile(Company company) {
        MakerPricingProfile profile = MakerPricingProfile.defaultForCompany(company.getId());
        if (company.getMinOrderValue() != null && company.getMinOrderValue().signum() > 0) {
            profile.setMinimumOrderFeeBrl(scale2(company.getMinOrderValue()));
        }
        if (company.getMinLeadTimeDays() != null && company.getMinLeadTimeDays() >= 1) {
            profile.setLeadTimeDays(Math.min(90, company.getMinLeadTimeDays()));
        }
        return profile;
    }

    private List<MaterialPriceItem> deriveInitialMaterialsFromCompany(Company company) {
        List<String> raw = jsonCodec.readStringList(company.getMaterialsJson());
        List<MaterialPriceItem> items = new ArrayList<>();
        for (String token : raw) {
            MaterialType parsed = parseMaterial(token);
            if (parsed != null && items.stream().noneMatch(i -> i.material() == parsed)) {
                items.add(new MaterialPriceItem(
                        parsed,
                        DEFAULT_MATERIAL_PRICES.getOrDefault(parsed, new BigDecimal("0.2500")),
                        true,
                        null
                ));
            }
        }
        if (items.isEmpty()) {
            items.add(new MaterialPriceItem(MaterialType.PLA, DEFAULT_MATERIAL_PRICES.get(MaterialType.PLA), true, null));
        }
        return items;
    }

    private static MaterialType parseMaterial(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return MaterialType.valueOf(token.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static BigDecimal resolveDim(BigDecimal overrideVal, BigDecimal catalogVal) {
        if (overrideVal != null && overrideVal.signum() > 0) {
            return overrideVal;
        }
        return catalogVal;
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale4(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale2OrDefault(BigDecimal value, BigDecimal fallback) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : fallback;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<MaterialType, BigDecimal> createDefaultPrices() {
        Map<MaterialType, BigDecimal> map = new EnumMap<>(MaterialType.class);
        map.put(MaterialType.PLA, new BigDecimal("0.2000"));
        map.put(MaterialType.PETG, new BigDecimal("0.2300"));
        map.put(MaterialType.ABS, new BigDecimal("0.2400"));
        map.put(MaterialType.ASA, new BigDecimal("0.2500"));
        map.put(MaterialType.TPU, new BigDecimal("0.2700"));
        map.put(MaterialType.NYLON, new BigDecimal("0.3000"));
        map.put(MaterialType.PC, new BigDecimal("0.3300"));
        map.put(MaterialType.CARBON_FIBER, new BigDecimal("0.4200"));
        map.put(MaterialType.PETG_CF, new BigDecimal("0.3800"));
        map.put(MaterialType.PA_CF, new BigDecimal("0.4800"));
        map.put(MaterialType.PVA, new BigDecimal("0.4500"));
        map.put(MaterialType.HIPS, new BigDecimal("0.2600"));
        map.put(MaterialType.RESIN, new BigDecimal("0.3500"));
        return map;
    }
}
