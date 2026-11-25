package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.http.request.company.RegisterMakerPrinterRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
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

        return companyRepository.save(company);
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
                        catalogById
                ))
                .toList();
    }

    private CompanyChoiceDTO toChoiceDto(
            CompanyAndAddress match,
            List<Asset> activePrinters,
            Map<Long, PrinterCatalog> catalogById
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
                makerEligibilityService.computeMaxBuildVolume(activePrinters, catalogById)
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
