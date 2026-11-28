package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.http.request.company.RegisterMakerPrinterRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerMaterialPrice;
import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.CompanyAndAddress;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.cache.MakerPresenceCache;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyDAO;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.SlugUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyService {
    private static final String DEFAULT_PRINTER_CONFIG_JSON =
            "{\"autoAcceptOrders\":false,\"apiEnabled\":false,\"maxLayerHeightMm\":0.20,\"defaultInfillPercent\":20,\"maxPrintTimeHours\":36,\"linkedFilamentGuids\":[]}";
    private static final String DEFAULT_PRINTER_TELEMETRY_JSON =
            "{\"nozzleTempC\":25.0,\"nozzleTargetC\":0,\"bedTempC\":25.0,\"bedTargetC\":0,\"chamberTempC\":null,\"fanSpeedPercent\":0,\"currentLayer\":0,\"totalLayers\":0,\"totalPrintHours\":0}";

    private final AddressService addressService;
    private final CompanyRepository companyRepository;
    private final CompanyDAO companyDAO;
    private final MakerPresenceCache makerPresenceCache;
    private final AssetRepository assetRepository;
    private final PrinterCatalogRepository printerCatalogRepository;
    private final MakerEligibilityService makerEligibilityService;
    private final JsonCodec jsonCodec;

    private org.imprime.ai.api.repo.db.MakerPricingProfileRepository makerPricingProfileRepository;
    private org.imprime.ai.api.repo.db.MakerMaterialPriceRepository makerMaterialPriceRepository;
    private PrinterSpeedResolver printerSpeedResolver;
    private MarketplaceQuoteCalculator marketplaceQuoteCalculator;

    @Autowired(required = false)
    public void setMarketplacePricingRepositories(
            org.imprime.ai.api.repo.db.MakerPricingProfileRepository makerPricingProfileRepository,
            org.imprime.ai.api.repo.db.MakerMaterialPriceRepository makerMaterialPriceRepository,
            PrinterSpeedResolver printerSpeedResolver,
            MarketplaceQuoteCalculator marketplaceQuoteCalculator
    ) {
        this.makerPricingProfileRepository = makerPricingProfileRepository;
        this.makerMaterialPriceRepository = makerMaterialPriceRepository;
        this.printerSpeedResolver = printerSpeedResolver;
        this.marketplaceQuoteCalculator = marketplaceQuoteCalculator;
    }

    @Transactional
    public Company registerCompany(RegisterCompanyRequest request, User owner) {
        boolean existsByName = companyRepository.existsByName(request.name());
        if (existsByName) {
            throw new BadRequestException(MessageCd.COMPANY_NAME_ALREADY_REGISTERED);
        }

        boolean existsByDocument = companyRepository.existsByDocumentTypeAndDocument(request.documentType(), request.document());
        if (existsByDocument) {
            throw new BadRequestException(MessageCd.COMPANY_DOCUMENT_ALREADY_REGISTERED);
        }

        Company company = new Company();
        company.setOwnerId(owner.getId());

        company.setDocument(request.document());
        company.setDocumentType(request.documentType());

        company.setName(request.name());
        company.setSlug(SlugUtil.ensureUniqueSlug(request.name(), companyRepository::existsBySlug));
        if (owner.getAvatarUrl() != null && !owner.getAvatarUrl().isBlank()) {
            company.setAvatarUrl(owner.getAvatarUrl());
        }

        boolean acceptingQuotes = request.acceptingCustomQuotes() == null || request.acceptingCustomQuotes();
        boolean offersPostProcessing = Boolean.TRUE.equals(request.offersPostProcessing());
        boolean offersPainting = Boolean.TRUE.equals(request.offersPainting());
        boolean offersCadModeling = Boolean.TRUE.equals(request.offersCadModeling());
        boolean supportsExpress = Boolean.TRUE.equals(request.supportsExpressOrders());

        company.setAcceptingCustomQuotes(acceptingQuotes);
        company.setOffersPostProcessing(offersPostProcessing);
        company.setOffersPainting(offersPainting);
        company.setOffersCadModeling(offersCadModeling);
        company.setSupportsExpressOrders(supportsExpress);
        company.setMinLayerHeightMm(request.minLayerHeightMm());
        company.setTechnicalNotes(CatalogDtoMapper.trimToNull(request.technicalNotes(), 500));

        List<RegisterMakerPrinterRequest> requestedPrinters = resolveRequestedPrinters(request);
        List<PreparedAsset> preparedAssets = new ArrayList<>();
        Set<MaterialType> catalogMaterials = new LinkedHashSet<>();
        Set<MakerCapability> capabilities = new LinkedHashSet<>();
        PrinterCatalog firstCatalog = null;
        boolean anyPrinterMulticolor = false;
        int maxPrinterSlots = 1;

        for (RegisterMakerPrinterRequest reqPrinter : requestedPrinters) {
            PrinterCatalog catalog = printerCatalogRepository.findByGuidAndStatus(reqPrinter.printerCatalogGuid(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.PRINTER_CATALOG_NOT_FOUND));
            if (firstCatalog == null) {
                firstCatalog = catalog;
            }
            if (catalog.getTechnology() == PrinterTechnology.SLA || catalog.getTechnology() == PrinterTechnology.DLP) {
                capabilities.add(MakerCapability.SLA_RESIN);
            } else {
                capabilities.add(MakerCapability.FDM);
            }
            if (catalog.getSupportedMaterials() != null) {
                catalogMaterials.addAll(catalog.getSupportedMaterials());
            }

            boolean printerMulticolor = reqPrinter.supportsMulticolor() != null
                    ? reqPrinter.supportsMulticolor()
                    : Boolean.TRUE.equals(catalog.getSupportsMulticolor());
            int printerSlots = printerMulticolor
                    ? Math.max(
                            reqPrinter.multicolorSlots() != null
                                    ? reqPrinter.multicolorSlots()
                                    : (catalog.getMaxColorSlots() != null ? catalog.getMaxColorSlots() : 4),
                            2
                    )
                    : 1;
            if (printerMulticolor) {
                anyPrinterMulticolor = true;
                maxPrinterSlots = Math.max(maxPrinterSlots, printerSlots);
            }

            int qty = reqPrinter.quantity() != null && reqPrinter.quantity() > 0 ? reqPrinter.quantity() : 1;
            preparedAssets.add(new PreparedAsset(catalog, reqPrinter, printerMulticolor, printerSlots, qty));
        }

        boolean companySupportsMulticolor = request.supportsMulticolor() != null
                ? request.supportsMulticolor()
                : anyPrinterMulticolor;
        int companyMaxSlots = companySupportsMulticolor
                ? Math.max(request.maxMulticolorSlots() != null ? request.maxMulticolorSlots() : maxPrinterSlots, 2)
                : 1;

        company.setSupportsMulticolor(companySupportsMulticolor);
        company.setMaxMulticolorSlots(companyMaxSlots);

        if (firstCatalog != null) {
            company.setPrimaryPrinterCatalogId(firstCatalog.getId());
            company.setPrimaryPrinter(firstCatalog.getFullDisplayName());
        }

        if (capabilities.isEmpty()) {
            capabilities.add(MakerCapability.FDM);
        }
        if (companySupportsMulticolor) {
            capabilities.add(MakerCapability.MULTICOLOR_AMS);
        }
        if (offersPainting) {
            capabilities.add(MakerCapability.PAINTING);
        }
        if (offersPostProcessing) {
            capabilities.add(MakerCapability.POST_PROCESSING);
        }
        if (offersCadModeling) {
            capabilities.add(MakerCapability.MODELING);
        }
        company.setCapabilitiesJson(jsonCodec.toEnumArrayJson(new ArrayList<>(capabilities)));

        List<MaterialType> resolvedMaterials = (request.materials() != null && !request.materials().isEmpty())
                ? request.materials()
                : new ArrayList<>(catalogMaterials);
        company.setMaterialsJson(jsonCodec.toEnumArrayJson(resolvedMaterials));

        company = companyRepository.save(company);

        if (!preparedAssets.isEmpty()) {
            List<Asset> assetsToSave = new ArrayList<>();
            int counter = 1;
            for (PreparedAsset prep : preparedAssets) {
                PrinterCatalog catalog = prep.catalog();
                RegisterMakerPrinterRequest reqPrinter = prep.request();
                String baseName = CatalogDtoMapper.firstNonBlank(reqPrinter.nickname(), catalog.getFullDisplayName());
                for (int i = 0; i < prep.quantity(); i++) {
                    Asset asset = new Asset();
                    asset.setCompanyId(company.getId());
                    asset.setPrinterCatalogId(catalog.getId());
                    asset.setName(prep.quantity() > 1 ? baseName + " #" + (i + 1) : baseName);
                    asset.setMake(catalog.getBrand());
                    asset.setModel(catalog.getModelName());
                    asset.setManufactureYear(catalog.getReleaseYear() != null ? catalog.getReleaseYear().shortValue() : (short) 2025);
                    asset.setTrayWidth(catalog.getBuildVolumeXMm());
                    asset.setTrayDepth(catalog.getBuildVolumeYMm());
                    asset.setTrayHeight(catalog.getBuildVolumeZMm());
                    asset.setNozzleDiameterMm(reqPrinter.nozzleDiameterMm() != null
                            ? reqPrinter.nozzleDiameterMm()
                            : (catalog.getDefaultNozzleMm() != null ? catalog.getDefaultNozzleMm() : new BigDecimal("0.400")));
                    asset.setPowerWatts(catalog.getAvgPowerWatts() != null ? catalog.getAvgPowerWatts() : 300);
                    asset.setPurchasePrice(catalog.getEstimatedPriceBrl());
                    asset.setPrinterStatus(PrinterStatus.ONLINE);
                    asset.setSupportsMulticolor(prep.multicolor());
                    asset.setMulticolorSlots(prep.slots());
                    asset.setIsEnclosedOverride(reqPrinter.isEnclosedOverride());
                    asset.setConfigJson(DEFAULT_PRINTER_CONFIG_JSON);
                    asset.setTelemetryJson(DEFAULT_PRINTER_TELEMETRY_JSON);
                    assetsToSave.add(asset);
                    counter++;
                }
            }
            if (!assetsToSave.isEmpty()) {
                assetRepository.saveAll(assetsToSave);
            }
        }

        Address address = addressService.registerAddress(request.address(), EntityType.COMPANY, company.getId(), true);
        company.setAddressId(address.getId());

        Company saved = companyRepository.save(company);
        seedDefaultMarketplacePricing(saved, resolvedMaterials);
        return saved;
    }

    private void seedDefaultMarketplacePricing(Company company, List<MaterialType> resolvedMaterials) {
        if (makerPricingProfileRepository != null
                && makerPricingProfileRepository.findByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE).isEmpty()) {
            MakerPricingProfile profile = MakerPricingProfile.defaultForCompany(company.getId());
            if (company.getMinOrderValue() != null && company.getMinOrderValue().signum() > 0) {
                profile.setMinimumOrderFeeBrl(company.getMinOrderValue());
            }
            if (company.getMinLeadTimeDays() != null && company.getMinLeadTimeDays() >= 1) {
                profile.setLeadTimeDays(Math.min(90, company.getMinLeadTimeDays()));
            }
            profile.setStatus(StatusCd.ACTIVE);
            makerPricingProfileRepository.save(profile);
        }
        if (makerMaterialPriceRepository != null) {
            List<MaterialType> materials = (resolvedMaterials == null || resolvedMaterials.isEmpty())
                    ? List.of(MaterialType.PLA)
                    : resolvedMaterials.stream().distinct().toList();
            List<MakerMaterialPrice> toSave = new ArrayList<>();
            for (MaterialType mat : materials) {
                MakerMaterialPrice price = new MakerMaterialPrice();
                price.setCompanyId(company.getId());
                price.setMaterial(mat);
                price.setPricePerGramBrl(defaultPricePerGram(mat));
                price.setEnabled(Boolean.TRUE);
                price.setStatus(StatusCd.ACTIVE);
                toSave.add(price);
            }
            makerMaterialPriceRepository.saveAll(toSave);
        }
    }

    private static BigDecimal defaultPricePerGram(MaterialType material) {
        return switch (material) {
            case PLA -> new BigDecimal("0.2000");
            case PETG -> new BigDecimal("0.2300");
            case ABS -> new BigDecimal("0.2400");
            case ASA -> new BigDecimal("0.2500");
            case TPU -> new BigDecimal("0.2700");
            case NYLON -> new BigDecimal("0.3000");
            case PC -> new BigDecimal("0.3300");
            case CARBON_FIBER -> new BigDecimal("0.4200");
            case PETG_CF -> new BigDecimal("0.3800");
            case PA_CF -> new BigDecimal("0.4800");
            case PVA -> new BigDecimal("0.4500");
            case HIPS -> new BigDecimal("0.2600");
            case RESIN -> new BigDecimal("0.3500");
            case OTHER -> new BigDecimal("0.2500");
        };
    }

    private List<RegisterMakerPrinterRequest> resolveRequestedPrinters(RegisterCompanyRequest request) {
        if (request.printers() != null && !request.printers().isEmpty()) {
            return request.printers();
        }
        if (request.primaryPrinterCatalogGuid() != null && !request.primaryPrinterCatalogGuid().isBlank()) {
            return List.of(new RegisterMakerPrinterRequest(
                    request.primaryPrinterCatalogGuid().trim(),
                    null,
                    1,
                    request.supportsMulticolor(),
                    request.maxMulticolorSlots(),
                    null,
                    null
            ));
        }
        return List.of();
    }

    public Optional<Company> findByOwnerId(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            log.error("Invalid ownerId [{}] passed to findByOwnerId.", ownerId);
            return Optional.empty();
        }

        return companyRepository.findByOwnerId(ownerId);
    }

    public List<CompanyChoiceDTO> findCompaniesForAddress(Address address, int max) {
        return findCompaniesForOrder(address, List.of(), max);
    }

    public List<CompanyChoiceDTO> findCompaniesForOrder(Address address, List<OrderFileAsset> plates, int max) {
        if (address == null || address.getLatitude() == null || address.getLongitude() == null || max <= 0) {
            return List.of();
        }

        int fetchLimit = Math.min(Math.max(max * 3, 100), 500);
        List<CompanyAndAddress> nearest = companyDAO.findNearestCompaniesByAddress(
                address.getLatitude(),
                address.getLongitude(),
                fetchLimit
        );
        if (nearest.isEmpty()) {
            return List.of();
        }

        List<Long> companyIds = nearest.stream()
                .map(match -> match.company().getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, List<Asset>> assetsByCompanyId = new HashMap<>();
        Set<Long> catalogIds = new LinkedHashSet<>();
        if (!companyIds.isEmpty()) {
            List<Asset> assets = assetRepository.findAllByCompanyIdInAndStatus(companyIds, StatusCd.ACTIVE);
            for (Asset asset : assets) {
                assetsByCompanyId.computeIfAbsent(asset.getCompanyId(), k -> new ArrayList<>()).add(asset);
                if (asset.getPrinterCatalogId() != null) {
                    catalogIds.add(asset.getPrinterCatalogId());
                }
            }
        }

        Map<Long, PrinterCatalog> catalogById = catalogIds.isEmpty()
                ? Map.of()
                : printerCatalogRepository.findAllByIdIn(catalogIds).stream()
                .collect(Collectors.toMap(PrinterCatalog::getId, pc -> pc, (a, b) -> a));

        Map<Long, org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.MakerInstantQuoteItem> instantQuotesByCompany =
                computeInstantQuotesForOrder(nearest, assetsByCompanyId, catalogById, plates);

        return nearest.stream()
                .filter(match -> makerEligibilityService.isEligible(
                        match.company(),
                        assetsByCompanyId.getOrDefault(match.company().getId(), List.of()),
                        catalogById,
                        plates
                ))
                .limit(max)
                .map(match -> toChoiceDto(
                        match,
                        assetsByCompanyId.getOrDefault(match.company().getId(), List.of()),
                        catalogById,
                        instantQuotesByCompany.get(match.company().getId())
                ))
                .toList();
    }

    private Map<Long, org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.MakerInstantQuoteItem> computeInstantQuotesForOrder(
            List<CompanyAndAddress> nearest,
            Map<Long, List<Asset>> assetsByCompanyId,
            Map<Long, PrinterCatalog> catalogById,
            List<OrderFileAsset> plates
    ) {
        if (plates == null || plates.isEmpty()
                || makerPricingProfileRepository == null
                || makerMaterialPriceRepository == null
                || printerSpeedResolver == null
                || marketplaceQuoteCalculator == null) {
            return Map.of();
        }
        boolean allHaveDna = plates.stream().allMatch(p ->
                p.getClientModelWeightGrams() != null
                        && p.getClientBasePrintTimeSeconds() != null
                        && p.getClientBasePrintTimeSeconds() > 0
                        && p.getDimensionXMm() != null
                        && p.getDimensionYMm() != null
                        && p.getDimensionZMm() != null
                        && MakerEligibilityService.parseMaterial(p.getMaterial()) != null);
        if (!allHaveDna) {
            return Map.of();
        }

        List<Long> companyIds = nearest.stream().map(m -> m.company().getId()).filter(Objects::nonNull).distinct().toList();
        Map<Long, MakerPricingProfile> profiles = makerPricingProfileRepository
                .findAllByCompanyIdInAndStatus(companyIds, StatusCd.ACTIVE).stream()
                .collect(Collectors.toMap(MakerPricingProfile::getCompanyId, p -> p, (a, b) -> a));
        Map<Long, Map<MaterialType, BigDecimal>> prices = new HashMap<>();
        for (MakerMaterialPrice row : makerMaterialPriceRepository.findAllByCompanyIdInAndStatus(companyIds, StatusCd.ACTIVE)) {
            if (Boolean.TRUE.equals(row.getEnabled()) && row.getMaterial() != null && row.getPricePerGramBrl() != null) {
                prices.computeIfAbsent(row.getCompanyId(), k -> new java.util.EnumMap<>(MaterialType.class))
                        .put(row.getMaterial(), row.getPricePerGramBrl());
            }
        }

        List<MakerEligibilityService.PlateMatchSpec> specs = plates.stream()
                .map(p -> new MakerEligibilityService.PlateMatchSpec(
                        p.getPlateNumber() != null ? p.getPlateNumber() : 1,
                        MakerEligibilityService.parseMaterial(p.getMaterial()),
                        p.getDimensionXMm(),
                        p.getDimensionYMm(),
                        p.getDimensionZMm(),
                        Boolean.TRUE.equals(p.getIsMultiColor()),
                        p.getColorCount() != null ? p.getColorCount() : 1
                ))
                .toList();

        Map<Long, org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.MakerInstantQuoteItem> result = new HashMap<>();
        for (CompanyAndAddress match : nearest) {
            Company company = match.company();
            MakerPricingProfile profile = profiles.getOrDefault(company.getId(), MakerPricingProfile.defaultForCompany(company.getId()));
            if (!Boolean.TRUE.equals(profile.getInstantQuoteEnabled())) {
                continue;
            }
            Map<MaterialType, BigDecimal> companyPrices = prices.getOrDefault(company.getId(), Map.of());
            var printerMatch = makerEligibilityService.matchPrintersForInstantQuote(
                    company,
                    assetsByCompanyId.getOrDefault(company.getId(), List.of()),
                    catalogById,
                    specs,
                    companyPrices.keySet(),
                    printerSpeedResolver
            );
            if (!printerMatch.eligible() || printerMatch.primaryPrinter() == null) {
                continue;
            }
            List<MarketplaceQuoteCalculator.PlateQuoteInput> calcInputs = new ArrayList<>(plates.size());
            for (int i = 0; i < plates.size(); i++) {
                OrderFileAsset plate = plates.get(i);
                int plateNum = plate.getPlateNumber() != null ? plate.getPlateNumber() : (i + 1);
                var mp = printerMatch.plateMatches().get(plateNum);
                calcInputs.add(new MarketplaceQuoteCalculator.PlateQuoteInput(
                        plateNum,
                        plate.getPlateName() != null ? plate.getPlateName() : ("Mesa " + plateNum),
                        MakerEligibilityService.parseMaterial(plate.getMaterial()),
                        plate.getQuantity() != null ? plate.getQuantity() : 1,
                        plate.getClientModelWeightGrams(),
                        plate.getClientSupportWeightGrams() != null ? plate.getClientSupportWeightGrams() : BigDecimal.ZERO,
                        plate.getClientBasePrintTimeSeconds(),
                        plate.getFinish(),
                        plate.getUrgency(),
                        mp.assetId(),
                        mp.printerGuid(),
                        mp.printerName(),
                        mp.printerModel(),
                        mp.speedTier(),
                        mp.speedMultiplier()
                ));
            }
            var calculated = marketplaceQuoteCalculator.calculate(profile, companyPrices, calcInputs);
            var primary = printerMatch.primaryPrinter();
            double distKm = match.distanceMeters() != null
                    ? BigDecimal.valueOf(match.distanceMeters() / 1000.0d).setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0d;
            result.put(company.getId(), new org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.MakerInstantQuoteItem(
                    company.getGuid(),
                    company.getSlug(),
                    company.getName(),
                    company.getAvatarUrl(),
                    Boolean.TRUE.equals(company.getVerified()),
                    makerPresenceCache.isCompanyOnline(company),
                    company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    company.getReviewsCount() != null ? company.getReviewsCount() : 0,
                    company.getPrintsDone() != null ? company.getPrintsDone() : 0,
                    match.address() != null ? match.address().getCity() : null,
                    match.address() != null ? match.address().getState() : null,
                    distKm,
                    new org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.MatchedPrinterDto(
                            primary.printerGuid(),
                            primary.printerName(),
                            primary.printerModel(),
                            primary.speedTier(),
                            primary.speedMultiplier(),
                            new org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.BoundingBoxMmDto(
                                    primary.maxX(), primary.maxY(), primary.maxZ()
                            )
                    ),
                    calculated.totalEstimatedPrintTimeSeconds(),
                    calculated.estimatedLeadTimeDays(),
                    calculated.totalPriceBrl(),
                    calculated.minimumOrderFeeApplied(),
                    new org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.PriceBreakdownDto(
                            calculated.materialCostBrl(),
                            calculated.machineCostBrl(),
                            calculated.supportCostBrl(),
                            calculated.setupFeeBrl(),
                            calculated.finishAndUrgencyAdjustmentBrl(),
                            calculated.minimumOrderDeltaBrl()
                    ),
                    calculated.plateQuotes().stream()
                            .map(pq -> new org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.PlateQuoteItemDto(
                                    pq.plateNumber(),
                                    pq.plateName(),
                                    pq.material().name(),
                                    pq.quantity(),
                                    pq.printerModel(),
                                    pq.speedTier(),
                                    pq.speedMultiplier(),
                                    pq.estimatedPrintTimeSeconds(),
                                    pq.totalPriceBrl()
                            ))
                            .toList()
            ));
        }
        return result;
    }

    private CompanyChoiceDTO toChoiceDto(
            CompanyAndAddress match,
            List<Asset> activePrinters,
            Map<Long, PrinterCatalog> catalogById,
            org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.MakerInstantQuoteItem instantQuote
    ) {
        double meters = match.distanceMeters();
        boolean useKilometers = meters > 1000;
        double value = useKilometers ? meters / 1000.0 : meters;
        String unit = useKilometers ? "km" : "m";

        Company company = match.company();
        List<MakerCapability> caps = jsonCodec.readEnumList(company.getCapabilitiesJson(), MakerCapability.class);
        boolean multicolor = Boolean.TRUE.equals(company.getSupportsMulticolor())
                || caps.contains(MakerCapability.MULTICOLOR_AMS)
                || activePrinters.stream().anyMatch(a -> Boolean.TRUE.equals(a.getSupportsMulticolor()));
        int slots = multicolor
                ? Math.max(company.getMaxMulticolorSlots() != null ? company.getMaxMulticolorSlots() : 4, 2)
                : 1;

        return new CompanyChoiceDTO(
                company.getName(),
                company.getGuid(),
                AddressDTO.from(match.address()),
                new CompanyChoiceDTO.Distance(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP), unit),
                makerPresenceCache.isCompanyOnline(company),
                company.getPrimaryPrinter(),
                multicolor,
                slots,
                !Boolean.FALSE.equals(company.getAcceptingCustomQuotes()),
                jsonCodec.readEnumList(company.getMaterialsJson(), MaterialType.class),
                company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO,
                company.getReviewsCount() != null ? company.getReviewsCount() : 0,
                company.getPrintsDone() != null ? company.getPrintsDone() : 0,
                Boolean.TRUE.equals(company.getVerified()),
                company.getAvatarUrl(),
                company.getMinLeadTimeDays() != null ? company.getMinLeadTimeDays() : 2,
                company.getMaxLeadTimeDays() != null ? company.getMaxLeadTimeDays() : 5,
                makerEligibilityService.computeMaxBuildVolume(activePrinters, catalogById),
                instantQuote
        );
    }

    private record PreparedAsset(
            PrinterCatalog catalog,
            RegisterMakerPrinterRequest request,
            boolean multicolor,
            int slots,
            int quantity
    ) {
    }

    public Optional<Company> findByCompanyGuid(String guid) {
        return companyRepository.findByGuid(guid);
    }

    public List<User> findAllMembers(Company company) {
        return findAllMembers(company.getGuid());
    }

    public List<User> findAllMembers(String companyId) {
        User owner = companyRepository.findOwnerByCompanyGuid(companyId).orElseThrow();
        return List.of(owner);
    }
}
