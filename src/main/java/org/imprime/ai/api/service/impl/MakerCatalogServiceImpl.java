package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.maker.MakerCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.maker.MakerCatalogItemResponse;
import org.imprime.ai.api.http.response.maker.MakerProfileResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerPortfolioItem;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ShippingOption;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerPortfolioItemRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.service.MakerCatalogService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.imprime.ai.api.util.PageableSortMapper;
import org.imprime.ai.api.util.SlugUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MakerCatalogServiceImpl implements MakerCatalogService {
    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final Map<String, String> PORTFOLIO_SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "updatedAt", "updateDt",
            "title", "title",
            "printTimeMinutes", "printTimeMinutes"
    );
    private static final Sort PORTFOLIO_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createDt");

    private static final Map<String, String> PRODUCT_SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "updatedAt", "updateDt",
            "name", "name",
            "price", "price",
            "rating", "ratingAvg",
            "salesCount", "salesCount",
            "reviewsCount", "reviewsCount"
    );
    private static final Sort PRODUCT_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createDt");

    private final CompanyRepository companyRepository;
    private final AddressRepository addressRepository;
    private final MakerPortfolioItemRepository portfolioItemRepository;
    private final ProductRepository productRepository;
    private final FavoriteServiceImpl favoriteService;
    private final OrderViewAssembler orderViewAssembler;
    private final JsonCodec jsonCodec;

    @Override
    public PageResponse<MakerCatalogItemResponse> listMakers(MakerCatalogFilter filter, Pageable pageable) {
        List<Company> companies = companyRepository.findAllByStatus(StatusCd.ACTIVE);
        if (companies.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        List<Long> companyIds = companies.stream().map(Company::getId).toList();
        Map<Long, Address> addressesByCompany = CatalogDtoMapper.indexPrimaryAddressesByCompanyId(
                addressRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(EntityType.COMPANY, companyIds, StatusCd.ACTIVE)
        );
        Set<Long> favoritedIds = favoriteService.findFavoritedTargetIds(FavoriteTargetType.MAKER);

        List<MakerCatalogItemResponse> items = companies.stream()
                .map(company -> toCatalogItem(company, addressesByCompany.get(company.getId()), filter, favoritedIds))
                .filter(item -> matchesFilter(item, filter))
                .sorted(buildComparator(pageable))
                .toList();

        return PageUtils.paginate(items, pageable);
    }

    @Override
    public MakerProfileResponse getMakerProfile(String slugOrGuid) {
        Company company = resolveActiveMaker(slugOrGuid);
        Address address = CatalogDtoMapper.indexPrimaryAddressesByCompanyId(
                addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.COMPANY, company.getId(), StatusCd.ACTIVE)
        ).get(company.getId());

        boolean isOwner = ServiceContext.getUser()
                .map(user -> user.getCompanyId() != null && user.getCompanyId().equals(company.getId()))
                .orElse(false);
        boolean favorited = favoriteService.isFavorited(FavoriteTargetType.MAKER, company.getId());

        return new MakerProfileResponse(
                company.getGuid(),
                company.getSlug(),
                company.getHandle(),
                company.getName(),
                company.getTagline(),
                company.getBio(),
                address != null ? address.getCity() : null,
                address != null ? address.getState() : null,
                company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO,
                company.getReviewsCount() != null ? company.getReviewsCount() : 0,
                company.getPrintsDone() != null ? company.getPrintsDone() : 0,
                company.getCreateDt(),
                company.getAvatarUrl(),
                company.getCoverUrl(),
                jsonCodec.readEnumList(company.getCapabilitiesJson(), MakerCapability.class),
                company.getResponseTimeHours(),
                company.getMinLeadTimeDays(),
                company.getMaxLeadTimeDays(),
                company.getMinOrderValue(),
                jsonCodec.readEnumList(company.getShippingOptionsJson(), ShippingOption.class),
                Boolean.TRUE.equals(company.getVacationMode()),
                company.getVacationMessage(),
                Boolean.TRUE.equals(company.getAcceptingCustomQuotes()),
                favorited,
                isOwner,
                CatalogDtoMapper.readSocialLinks(company, jsonCodec)
        );
    }

    @Override
    public PageResponse<PortfolioItemResponse> listMakerPortfolio(String slugOrGuid, Pageable pageable) {
        Company company = resolveActiveMaker(slugOrGuid);
        if (!Boolean.TRUE.equals(company.getPublicPortfolioEnabled())) {
            return PageResponse.from(Page.empty(PageableSortMapper.remap(pageable, PORTFOLIO_SORT_MAPPING, PORTFOLIO_DEFAULT_SORT)));
        }
        Pageable mappedPageable = PageableSortMapper.remap(pageable, PORTFOLIO_SORT_MAPPING, PORTFOLIO_DEFAULT_SORT);
        Page<MakerPortfolioItem> page = portfolioItemRepository.findAllByCompanyIdAndStatus(
                company.getId(), StatusCd.ACTIVE, mappedPageable
        );
        return PageResponse.from(page.map(item -> CatalogDtoMapper.toPortfolioItemResponse(item, jsonCodec)));
    }

    @Override
    public PageResponse<ProductCatalogItemResponse> listMakerProducts(String slugOrGuid, Pageable pageable) {
        Company company = resolveActiveMaker(slugOrGuid);
        Pageable mappedPageable = PageableSortMapper.remap(pageable, PRODUCT_SORT_MAPPING, PRODUCT_DEFAULT_SORT);
        Page<Product> page = productRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE, mappedPageable);
        Set<Long> favoritedIds = favoriteService.findFavoritedTargetIds(FavoriteTargetType.PRODUCT);
        MakerBriefResponse makerBrief = orderViewAssembler.toMakerBrief(company);

        return PageResponse.from(page.map(product -> CatalogDtoMapper.toProductCatalogItemResponse(
                product,
                makerBrief,
                favoritedIds.contains(product.getId()),
                jsonCodec
        )));
    }

    @Override
    public FavoriteStatusResponse addFavorite(String makerGuid) {
        Company company = companyRepository.findByGuidAndStatus(makerGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
        return favoriteService.upsertFavorite(FavoriteTargetType.MAKER, company.getId(), company.getGuid());
    }

    @Override
    public void removeFavorite(String makerGuid) {
        Company company = companyRepository.findByGuidAndStatus(makerGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
        favoriteService.removeFavoriteByTarget(FavoriteTargetType.MAKER, company.getId());
    }

    private Company resolveActiveMaker(String slugOrGuid) {
        Company found = companyRepository.findByGuidAndStatus(slugOrGuid, StatusCd.ACTIVE)
                .or(() -> companyRepository.findBySlugAndStatus(slugOrGuid, StatusCd.ACTIVE))
                .or(() -> companyRepository.findByOwnerGuid(slugOrGuid)
                        .filter(c -> StatusCd.ACTIVE.equals(c.getStatus())))
                .or(() -> {
                    if ("me".equalsIgnoreCase(slugOrGuid) || "studio-impressao-3d".equalsIgnoreCase(slugOrGuid)) {
                        return ServiceContext.getUser()
                                .filter(u -> u.getCompanyId() != null)
                                .flatMap(u -> companyRepository.findById(u.getCompanyId()))
                                .filter(c -> StatusCd.ACTIVE.equals(c.getStatus()));
                    }
                    return java.util.Optional.empty();
                })
                .orElseGet(() -> companyRepository.findAllByStatus(StatusCd.ACTIVE).stream()
                        .filter(company -> matchesCleanOrLegacySlug(company, slugOrGuid))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND)));
        normalizeLegacySlugIfApplicable(found);
        return found;
    }

    private boolean matchesCleanOrLegacySlug(Company company, String candidate) {
        if (company == null || candidate == null || candidate.isBlank() || company.getName() == null) {
            return false;
        }
        String cleanSlug = SlugUtil.slugify(company.getName());
        String legacySlug = company.getId() != null ? cleanSlug + "-" + company.getId() : cleanSlug;
        return cleanSlug.equalsIgnoreCase(candidate.trim()) || legacySlug.equalsIgnoreCase(candidate.trim());
    }

    private void normalizeLegacySlugIfApplicable(Company company) {
        if (company == null || company.getId() == null || company.getName() == null || company.getName().isBlank()) {
            return;
        }
        String cleanSlug = SlugUtil.slugify(company.getName());
        String idSuffix = "-" + company.getId();
        if (company.getSlug() == null
                || company.getSlug().isBlank()
                || company.getSlug().endsWith(idSuffix)) {
            if (!companyRepository.existsBySlugAndIdNot(cleanSlug, company.getId())) {
                company.setSlug(cleanSlug);
                companyRepository.save(company);
            }
        }
    }

    private MakerCatalogItemResponse toCatalogItem(
            Company company,
            Address address,
            MakerCatalogFilter filter,
            Set<Long> favoritedIds) {
        BigDecimal distanceKm = calculateDistanceKm(filter, address);
        List<MaterialType> materials = jsonCodec.readEnumList(company.getMaterialsJson(), MaterialType.class);
        List<String> tags = jsonCodec.readStringList(company.getTagsJson());

        return new MakerCatalogItemResponse(
                company.getGuid(),
                company.getSlug(),
                company.getName(),
                company.getSpecialty(),
                company.getSpecialtyCategory(),
                CatalogDtoMapper.firstNonBlank(company.getTagline(), company.getBio()),
                company.getPrimaryPrinter(),
                address != null ? address.getCity() : null,
                address != null ? address.getState() : null,
                distanceKm,
                company.getBasePrice(),
                company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO,
                company.getReviewsCount() != null ? company.getReviewsCount() : 0,
                company.getPrintsDone() != null ? company.getPrintsDone() : 0,
                company.getMinLeadTimeDays() != null ? company.getMinLeadTimeDays() : 2,
                materials,
                tags,
                company.getBadge(),
                company.getCoverUrl(),
                company.getAvatarUrl(),
                Boolean.TRUE.equals(company.getOnline()) && !Boolean.TRUE.equals(company.getVacationMode()),
                favoritedIds.contains(company.getId()),
                Boolean.TRUE.equals(company.getVerified()),
                company.getCreateDt()
        );
    }

    private boolean matchesFilter(MakerCatalogItemResponse item, MakerCatalogFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            String q = filter.query().trim().toLowerCase(Locale.ROOT);
            boolean textMatch = containsIgnoreCase(item.name(), q)
                    || containsIgnoreCase(item.specialty(), q)
                    || containsIgnoreCase(item.specialtyCategory(), q)
                    || containsIgnoreCase(item.summary(), q)
                    || containsIgnoreCase(item.city(), q)
                    || containsIgnoreCase(item.state(), q)
                    || item.tags().stream().anyMatch(tag -> containsIgnoreCase(tag, q));
            if (!textMatch) {
                return false;
            }
        }
        if (filter.specialty() != null && !filter.specialty().isBlank()) {
            String spec = filter.specialty().trim().toLowerCase(Locale.ROOT);
            boolean matchesSpecialty = (item.specialtyCategory() != null && item.specialtyCategory().equalsIgnoreCase(spec))
                    || containsIgnoreCase(item.specialty(), spec);
            if (!matchesSpecialty) {
                return false;
            }
        }
        if (filter.maxDistanceKm() != null) {
            if (item.distanceKm() == null || item.distanceKm().doubleValue() > filter.maxDistanceKm()) {
                return false;
            }
        }
        if (filter.minRating() != null) {
            if (item.rating() == null || item.rating().compareTo(filter.minRating()) < 0) {
                return false;
            }
        }
        if (filter.maxDeadlineDays() != null && item.minLeadTimeDays() > filter.maxDeadlineDays()) {
            return false;
        }
        if (filter.maxPrice() != null) {
            if (item.basePrice() == null || item.basePrice().compareTo(filter.maxPrice()) > 0) {
                return false;
            }
        }
        if (Boolean.TRUE.equals(filter.onlyOnline()) && !item.online()) {
            return false;
        }
        if (filter.materials() != null && !filter.materials().isEmpty()) {
            if (Collections.disjoint(item.materials(), filter.materials())) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal calculateDistanceKm(MakerCatalogFilter filter, Address address) {
        if (filter == null || filter.latitude() == null || filter.longitude() == null
                || address == null || address.getLatitude() == null || address.getLongitude() == null) {
            return null;
        }
        double lat1 = Math.toRadians(filter.latitude());
        double lon1 = Math.toRadians(filter.longitude());
        double lat2 = Math.toRadians(address.getLatitude());
        double lon2 = Math.toRadians(address.getLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double km = EARTH_RADIUS_KM * c;

        return BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP);
    }

    private Comparator<MakerCatalogItemResponse> buildComparator(Pageable pageable) {
        Comparator<MakerCatalogItemResponse> defaultComparator = Comparator
                .comparing(MakerCatalogItemResponse::rating, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MakerCatalogItemResponse::completedOrdersCount, Comparator.reverseOrder())
                .thenComparing(MakerCatalogItemResponse::name, Comparator.nullsLast(String::compareToIgnoreCase));

        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultComparator;
        }

        Comparator<MakerCatalogItemResponse> combined = null;
        for (Sort.Order order : pageable.getSort()) {
            Comparator<MakerCatalogItemResponse> next = switch (order.getProperty()) {
                case "rating", "ratingAvg" -> Comparator.comparing(
                        MakerCatalogItemResponse::rating, Comparator.nullsLast(BigDecimal::compareTo));
                case "distance", "distanceKm" -> Comparator.comparing(
                        MakerCatalogItemResponse::distanceKm, Comparator.nullsLast(BigDecimal::compareTo));
                case "price", "basePrice" -> Comparator.comparing(
                        MakerCatalogItemResponse::basePrice, Comparator.nullsLast(BigDecimal::compareTo));
                case "reviewsCount" -> Comparator.comparingInt(MakerCatalogItemResponse::reviewsCount);
                case "completedOrdersCount", "printsDone" -> Comparator.comparingInt(MakerCatalogItemResponse::completedOrdersCount);
                case "createdAt", "createDt" -> Comparator.comparing(
                        MakerCatalogItemResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
                case "name" -> Comparator.comparing(
                        MakerCatalogItemResponse::name, Comparator.nullsLast(String::compareToIgnoreCase));
                default -> null;
            };
            if (next != null) {
                if (order.isDescending() && !"distance".equals(order.getProperty()) && !"distanceKm".equals(order.getProperty())) {
                    next = next.reversed();
                } else if (order.isDescending()) {
                    next = Comparator.comparing(
                            MakerCatalogItemResponse::distanceKm,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    );
                }
                combined = combined == null ? next : combined.thenComparing(next);
            }
        }
        return combined != null ? combined : defaultComparator;
    }

    private boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }
}
