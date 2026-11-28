package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.InstantPrintQuoteRequest;
import org.imprime.ai.api.http.request.order.InstantPrintQuoteRequest.BoundingBoxMmInput;
import org.imprime.ai.api.http.request.order.InstantPrintQuoteRequest.PlateDnaInput;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.BoundingBoxMmDto;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.DnaSummaryDto;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.MakerInstantQuoteItem;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.MatchedPrinterDto;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.PlateQuoteItemDto;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse.PriceBreakdownDto;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerMaterialPrice;
import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.CompanyAndAddress;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.InstantQuoteSortBy;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyDAO;
import org.imprime.ai.api.repo.db.MakerMaterialPriceRepository;
import org.imprime.ai.api.repo.db.MakerPricingProfileRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.service.InstantPrintQuoteService;
import org.imprime.ai.api.service.MakerEligibilityService;
import org.imprime.ai.api.service.MakerEligibilityService.InstantQuoteMatchResult;
import org.imprime.ai.api.service.MakerEligibilityService.MatchedPlatePrinter;
import org.imprime.ai.api.service.MakerEligibilityService.PlateMatchSpec;
import org.imprime.ai.api.service.MarketplaceQuoteCalculator;
import org.imprime.ai.api.service.MarketplaceQuoteCalculator.CalculatedOrderQuote;
import org.imprime.ai.api.service.MarketplaceQuoteCalculator.PlateQuoteInput;
import org.imprime.ai.api.service.PrinterSpeedResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstantPrintQuoteServiceImpl implements InstantPrintQuoteService {

    private static final String DEFAULT_ENGINE_ID = "bambu";
    private static final String DEFAULT_PROFILE_ID = "Bambu Lab A1 0.4 nozzle";
    private static final double DEFAULT_RADIUS_KM = 50.0d;
    private static final int DEFAULT_MAX_RESULTS = 30;
    private static final int CANDIDATE_FETCH_MULTIPLIER = 3;

    private final CompanyDAO companyDAO;
    private final AddressRepository addressRepository;
    private final MakerPricingProfileRepository pricingProfileRepository;
    private final MakerMaterialPriceRepository materialPriceRepository;
    private final AssetRepository assetRepository;
    private final PrinterCatalogRepository printerCatalogRepository;
    private final MakerEligibilityService makerEligibilityService;
    private final PrinterSpeedResolver printerSpeedResolver;
    private final MarketplaceQuoteCalculator quoteCalculator;

    @Override
    @Transactional(readOnly = true)
    public InstantPrintQuoteResponse quoteNearbyMakers(InstantPrintQuoteRequest request) {
        Coordinates coords = resolveCoordinates(request);
        double radiusKm = request.maxDistanceKm() != null ? request.maxDistanceKm() : DEFAULT_RADIUS_KM;
        double radiusMeters = radiusKm * 1000.0d;
        int maxResults = request.maxResults() != null ? request.maxResults() : DEFAULT_MAX_RESULTS;
        InstantQuoteSortBy sortBy = request.sortBy() != null ? request.sortBy() : InstantQuoteSortBy.PRICE_ASC;

        List<NormalizedPlate> normalizedPlates = normalizePlates(request.plates());
        DnaSummaryDto dnaSummary = buildDnaSummary(normalizedPlates);

        int fetchLimit = Math.min(200, Math.max(50, maxResults * CANDIDATE_FETCH_MULTIPLIER));
        List<CompanyAndAddress> nearby = companyDAO.findNearestCompaniesByAddress(
                coords.latitude(),
                coords.longitude(),
                radiusMeters,
                fetchLimit
        );

        String engineId = hasText(request.slicerEngineId()) ? request.slicerEngineId().trim() : DEFAULT_ENGINE_ID;
        String profileId = hasText(request.slicerProfileId()) ? request.slicerProfileId().trim() : DEFAULT_PROFILE_ID;

        if (nearby == null || nearby.isEmpty()) {
            return new InstantPrintQuoteResponse(
                    "BRL", engineId, profileId, dnaSummary, 0, 0, List.of()
            );
        }

        List<Long> companyIds = nearby.stream()
                .map(ca -> ca.company().getId())
                .distinct()
                .toList();

        Map<Long, MakerPricingProfile> profilesByCompany = pricingProfileRepository
                .findAllByCompanyIdInAndStatus(companyIds, StatusCd.ACTIVE).stream()
                .collect(Collectors.toMap(MakerPricingProfile::getCompanyId, Function.identity(), (a, b) -> a));

        Map<Long, Map<MaterialType, BigDecimal>> pricesByCompany = new HashMap<>();
        for (MakerMaterialPrice row : materialPriceRepository.findAllByCompanyIdInAndStatus(companyIds, StatusCd.ACTIVE)) {
            if (Boolean.TRUE.equals(row.getEnabled()) && row.getMaterial() != null && row.getPricePerGramBrl() != null) {
                pricesByCompany
                        .computeIfAbsent(row.getCompanyId(), k -> new EnumMap<>(MaterialType.class))
                        .put(row.getMaterial(), row.getPricePerGramBrl());
            }
        }

        Map<Long, List<Asset>> fleetsByCompany = assetRepository
                .findAllByCompanyIdInAndStatus(companyIds, StatusCd.ACTIVE).stream()
                .collect(Collectors.groupingBy(Asset::getCompanyId));

        Set<Long> catalogIds = new HashSet<>();
        for (List<Asset> fleet : fleetsByCompany.values()) {
            for (Asset asset : fleet) {
                if (asset.getPrinterCatalogId() != null) {
                    catalogIds.add(asset.getPrinterCatalogId());
                }
            }
        }
        Map<Long, PrinterCatalog> catalogsById = catalogIds.isEmpty()
                ? Map.of()
                : printerCatalogRepository.findAllById(catalogIds).stream()
                .collect(Collectors.toMap(PrinterCatalog::getId, Function.identity()));

        List<PlateMatchSpec> matchSpecs = normalizedPlates.stream()
                .map(p -> new PlateMatchSpec(
                        p.plateNumber(),
                        p.material(),
                        p.boxX(),
                        p.boxY(),
                        p.boxZ(),
                        p.multiColor(),
                        p.colorCount()
                ))
                .toList();

        List<MakerInstantQuoteItem> quotes = new ArrayList<>();
        for (CompanyAndAddress ca : nearby) {
            Company company = ca.company();
            Address address = ca.address();
            MakerPricingProfile profile = profilesByCompany.getOrDefault(
                    company.getId(),
                    buildFallbackProfile(company)
            );
            if (!Boolean.TRUE.equals(profile.getInstantQuoteEnabled())) {
                continue;
            }

            Map<MaterialType, BigDecimal> materialPrices = pricesByCompany.getOrDefault(company.getId(), Map.of());
            List<Asset> fleet = fleetsByCompany.getOrDefault(company.getId(), List.of());

            InstantQuoteMatchResult match = makerEligibilityService.matchPrintersForInstantQuote(
                    company,
                    fleet,
                    catalogsById,
                    matchSpecs,
                    materialPrices.keySet(),
                    printerSpeedResolver
            );
            if (!match.eligible() || match.primaryPrinter() == null) {
                continue;
            }

            List<PlateQuoteInput> calcInputs = new ArrayList<>(normalizedPlates.size());
            for (NormalizedPlate plate : normalizedPlates) {
                MatchedPlatePrinter mp = match.plateMatches().get(plate.plateNumber());
                calcInputs.add(new PlateQuoteInput(
                        plate.plateNumber(),
                        plate.plateName(),
                        plate.material(),
                        plate.quantity(),
                        plate.modelWeightGrams(),
                        plate.supportWeightGrams(),
                        plate.basePrintTimeSeconds(),
                        plate.finish(),
                        plate.urgency(),
                        mp.assetId(),
                        mp.printerGuid(),
                        mp.printerName(),
                        mp.printerModel(),
                        mp.speedTier(),
                        mp.speedMultiplier()
                ));
            }

            CalculatedOrderQuote calculated = quoteCalculator.calculate(profile, materialPrices, calcInputs);
            MatchedPlatePrinter primary = match.primaryPrinter();
            double distanceKm = ca.distanceMeters() != null
                    ? BigDecimal.valueOf(ca.distanceMeters() / 1000.0d).setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0d;

            quotes.add(new MakerInstantQuoteItem(
                    company.getGuid(),
                    company.getSlug(),
                    company.getName(),
                    company.getAvatarUrl(),
                    Boolean.TRUE.equals(company.getVerified()),
                    Boolean.TRUE.equals(company.getOnline()),
                    company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    company.getReviewsCount() != null ? company.getReviewsCount() : 0,
                    company.getPrintsDone() != null ? company.getPrintsDone() : 0,
                    address != null ? address.getCity() : null,
                    address != null ? address.getState() : null,
                    distanceKm,
                    new MatchedPrinterDto(
                            primary.printerGuid(),
                            primary.printerName(),
                            primary.printerModel(),
                            primary.speedTier(),
                            primary.speedMultiplier(),
                            new BoundingBoxMmDto(primary.maxX(), primary.maxY(), primary.maxZ())
                    ),
                    calculated.totalEstimatedPrintTimeSeconds(),
                    calculated.estimatedLeadTimeDays(),
                    calculated.totalPriceBrl(),
                    calculated.minimumOrderFeeApplied(),
                    new PriceBreakdownDto(
                            calculated.materialCostBrl(),
                            calculated.machineCostBrl(),
                            calculated.supportCostBrl(),
                            calculated.setupFeeBrl(),
                            calculated.finishAndUrgencyAdjustmentBrl(),
                            calculated.minimumOrderDeltaBrl()
                    ),
                    calculated.plateQuotes().stream()
                            .map(pq -> new PlateQuoteItemDto(
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

        quotes.sort(comparatorFor(sortBy));
        int eligibleCount = quotes.size();
        List<MakerInstantQuoteItem> limitedQuotes = quotes.size() > maxResults
                ? quotes.subList(0, maxResults)
                : quotes;

        return new InstantPrintQuoteResponse(
                "BRL",
                engineId,
                profileId,
                dnaSummary,
                nearby.size(),
                eligibleCount,
                limitedQuotes
        );
    }

    private Coordinates resolveCoordinates(InstantPrintQuoteRequest request) {
        if (hasText(request.addressGuid())) {
            User user = ServiceContext.getUserOrThrow();
            Address address = addressRepository.findByGuidAndStatus(request.addressGuid().trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.ADDRESS_NOT_FOUND));
            boolean isUserOwned = address.getOwnerType() == EntityType.USER
                    && Objects.equals(address.getOwnerId(), user.getId());
            boolean isCompanyOwned = address.getOwnerType() == EntityType.COMPANY
                    && user.getCompanyId() != null
                    && Objects.equals(address.getOwnerId(), user.getCompanyId());
            if (!isUserOwned && !isCompanyOwned) {
                throw new ForbiddenException();
            }
            if (address.getLatitude() == null || address.getLongitude() == null) {
                throw new BadRequestException("O endereço selecionado não possui coordenadas geográficas.", MessageCd.VALIDATION_ERROR);
            }
            return new Coordinates(address.getLatitude(), address.getLongitude());
        }

        if (request.latitude() != null && request.longitude() != null) {
            return new Coordinates(request.latitude(), request.longitude());
        }

        User user = ServiceContext.getUser().orElse(null);
        if (user != null) {
            List<Address> userAddresses = addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                    EntityType.USER, user.getId(), StatusCd.ACTIVE
            );
            Address preferred = userAddresses.stream()
                    .filter(a -> a.getLatitude() != null && a.getLongitude() != null)
                    .sorted(Comparator.comparing((Address a) -> !Boolean.TRUE.equals(a.getDefaultAddress())))
                    .findFirst()
                    .orElse(null);
            if (preferred != null) {
                return new Coordinates(preferred.getLatitude(), preferred.getLongitude());
            }
        }

        throw new BadRequestException("Informe um addressGuid válido ou latitude/longitude para cotação.", MessageCd.VALIDATION_ERROR);
    }

    private List<NormalizedPlate> normalizePlates(List<PlateDnaInput> inputs) {
        List<NormalizedPlate> list = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            PlateDnaInput input = inputs.get(i);
            MaterialType material = MakerEligibilityService.parseMaterial(input.material());
            if (material == null) {
                throw new BadRequestException("Material inválido: " + input.material(), MessageCd.VALIDATION_ERROR);
            }
            int plateNum = input.plateNumber() != null ? input.plateNumber() : (i + 1);
            String plateName = hasText(input.plateName()) ? input.plateName().trim() : ("Mesa " + plateNum);
            BigDecimal supportWeight = input.supportWeightGrams() != null
                    ? input.supportWeightGrams().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BoundingBoxMmInput box = input.boundingBoxMm();
            boolean multi = Boolean.TRUE.equals(input.isMultiColor());
            int colors = input.colorCount() != null ? input.colorCount() : (multi ? 2 : 1);

            list.add(new NormalizedPlate(
                    plateNum,
                    plateName,
                    material,
                    input.quantity(),
                    input.modelWeightGrams().setScale(2, RoundingMode.HALF_UP),
                    supportWeight,
                    input.basePrintTimeSeconds(),
                    box.x().setScale(2, RoundingMode.HALF_UP),
                    box.y().setScale(2, RoundingMode.HALF_UP),
                    box.z().setScale(2, RoundingMode.HALF_UP),
                    input.finish(),
                    input.urgency(),
                    multi,
                    colors
            ));
        }
        return list;
    }

    private DnaSummaryDto buildDnaSummary(List<NormalizedPlate> plates) {
        int totalUnits = 0;
        BigDecimal totalModelWeight = BigDecimal.ZERO;
        BigDecimal totalSupportWeight = BigDecimal.ZERO;
        int totalBaseSeconds = 0;
        BigDecimal maxX = BigDecimal.ZERO;
        BigDecimal maxY = BigDecimal.ZERO;
        BigDecimal maxZ = BigDecimal.ZERO;

        for (NormalizedPlate p : plates) {
            BigDecimal qtyBd = BigDecimal.valueOf(p.quantity());
            totalUnits += p.quantity();
            totalModelWeight = totalModelWeight.add(p.modelWeightGrams().multiply(qtyBd));
            totalSupportWeight = totalSupportWeight.add(p.supportWeightGrams().multiply(qtyBd));
            totalBaseSeconds += p.basePrintTimeSeconds() * p.quantity();
            maxX = maxX.max(p.boxX());
            maxY = maxY.max(p.boxY());
            maxZ = maxZ.max(p.boxZ());
        }

        totalModelWeight = totalModelWeight.setScale(2, RoundingMode.HALF_UP);
        totalSupportWeight = totalSupportWeight.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalWeight = totalModelWeight.add(totalSupportWeight).setScale(2, RoundingMode.HALF_UP);

        return new DnaSummaryDto(
                plates.size(),
                totalUnits,
                totalModelWeight,
                totalSupportWeight,
                totalWeight,
                totalBaseSeconds,
                new BoundingBoxMmDto(maxX, maxY, maxZ)
        );
    }

    private MakerPricingProfile buildFallbackProfile(Company company) {
        MakerPricingProfile profile = MakerPricingProfile.defaultForCompany(company.getId());
        if (company.getMinOrderValue() != null && company.getMinOrderValue().signum() > 0) {
            profile.setMinimumOrderFeeBrl(company.getMinOrderValue());
        }
        if (company.getMinLeadTimeDays() != null && company.getMinLeadTimeDays() >= 1) {
            profile.setLeadTimeDays(Math.min(90, company.getMinLeadTimeDays()));
        }
        return profile;
    }

    private static Comparator<MakerInstantQuoteItem> comparatorFor(InstantQuoteSortBy sortBy) {
        Comparator<MakerInstantQuoteItem> secondary = Comparator
                .comparing(MakerInstantQuoteItem::totalPriceBrl)
                .thenComparing(MakerInstantQuoteItem::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()));
        return switch (sortBy) {
            case PRICE_ASC -> secondary;
            case DISTANCE_ASC -> Comparator
                    .comparing(MakerInstantQuoteItem::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(MakerInstantQuoteItem::totalPriceBrl);
            case PRINT_TIME_ASC -> Comparator
                    .comparingInt(MakerInstantQuoteItem::estimatedPrintTimeSeconds)
                    .thenComparing(MakerInstantQuoteItem::totalPriceBrl);
            case LEAD_TIME_ASC -> Comparator
                    .comparingInt(MakerInstantQuoteItem::leadTimeDays)
                    .thenComparing(MakerInstantQuoteItem::totalPriceBrl);
            case RATING_DESC -> Comparator
                    .comparing(MakerInstantQuoteItem::rating, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(MakerInstantQuoteItem::totalPriceBrl);
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Coordinates(double latitude, double longitude) {
    }

    private record NormalizedPlate(
            int plateNumber,
            String plateName,
            MaterialType material,
            int quantity,
            BigDecimal modelWeightGrams,
            BigDecimal supportWeightGrams,
            int basePrintTimeSeconds,
            BigDecimal boxX,
            BigDecimal boxY,
            BigDecimal boxZ,
            String finish,
            String urgency,
            boolean multiColor,
            int colorCount
    ) {
    }
}
