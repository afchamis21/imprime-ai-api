package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.product.ProductCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.imprime.ai.api.http.response.product.ProductDetailResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.service.ProductService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final FavoriteServiceImpl favoriteService;
    private final OrderViewAssembler orderViewAssembler;
    private final JsonCodec jsonCodec;

    @Override
    public PageResponse<ProductCatalogItemResponse> listProducts(ProductCatalogFilter filter, Pageable pageable) {
        List<Product> activeProducts = productRepository.findAllByStatus(StatusCd.ACTIVE);
        if (activeProducts.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> companyIds = activeProducts.stream()
                .map(Product::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Company> companiesById = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        Set<Long> favoritedIds = favoriteService.findFavoritedTargetIds(FavoriteTargetType.PRODUCT);

        List<Product> filtered = activeProducts.stream()
                .filter(product -> matchesFilter(product, companiesById.get(product.getCompanyId()), filter))
                .sorted(buildComparator(pageable))
                .toList();

        List<ProductCatalogItemResponse> mapped = filtered.stream()
                .map(product -> {
                    MakerBriefResponse makerBrief = orderViewAssembler.toMakerBrief(companiesById.get(product.getCompanyId()));
                    return CatalogDtoMapper.toProductCatalogItemResponse(
                            product,
                            makerBrief,
                            favoritedIds.contains(product.getId()),
                            jsonCodec
                    );
                })
                .toList();

        return PageUtils.paginate(mapped, pageable);
    }

    @Override
    public ProductDetailResponse getProductDetail(String slugOrGuid) {
        Product product = productRepository.findByGuidAndStatus(slugOrGuid, StatusCd.ACTIVE)
                .or(() -> productRepository.findBySlugAndStatus(slugOrGuid, StatusCd.ACTIVE))
                .orElseThrow(() -> new NotFoundException(MessageCd.PRODUCT_NOT_FOUND));

        Company company = product.getCompanyId() != null
                ? companyRepository.findById(product.getCompanyId()).orElse(null)
                : null;
        MakerBriefResponse makerBrief = orderViewAssembler.toMakerBrief(company);
        boolean favorited = favoriteService.isFavorited(FavoriteTargetType.PRODUCT, product.getId());

        List<String> imageUrls = jsonCodec.readStringList(product.getImageUrlsJson());
        if (imageUrls.isEmpty() && product.getCoverImageUrl() != null && !product.getCoverImageUrl().isBlank()) {
            imageUrls = List.of(product.getCoverImageUrl());
        }

        return new ProductDetailResponse(
                product.getGuid(),
                product.getSlug(),
                product.getName(),
                product.getPrice(),
                product.getOldPrice(),
                product.getCategory(),
                product.getRatingAvg() != null ? product.getRatingAvg() : BigDecimal.ZERO,
                product.getReviewsCount() != null ? product.getReviewsCount() : 0,
                product.getSalesCount() != null ? product.getSalesCount() : 0,
                product.getDescription(),
                jsonCodec.readStringList(product.getFeaturesJson()),
                product.getMaterial(),
                CatalogDtoMapper.toDimensionsDto(product.getWidthMm(), product.getDepthMm(), product.getHeightMm()),
                product.getWeightGrams(),
                product.getProductionDays(),
                jsonCodec.readStringList(product.getCustomizableColorsJson()),
                Boolean.TRUE.equals(product.getInStock()),
                favorited,
                imageUrls,
                makerBrief,
                product.getCreateDt()
        );
    }

    @Override
    public FavoriteStatusResponse addFavorite(String productGuid) {
        Product product = productRepository.findByGuidAndStatus(productGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PRODUCT_NOT_FOUND));
        return favoriteService.upsertFavorite(FavoriteTargetType.PRODUCT, product.getId(), product.getGuid());
    }

    @Override
    public void removeFavorite(String productGuid) {
        Product product = productRepository.findByGuidAndStatus(productGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PRODUCT_NOT_FOUND));
        favoriteService.removeFavoriteByTarget(FavoriteTargetType.PRODUCT, product.getId());
    }

    private boolean matchesFilter(Product product, Company maker, ProductCatalogFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            String q = filter.query().trim().toLowerCase(Locale.ROOT);
            boolean textMatch = containsIgnoreCase(product.getName(), q)
                    || containsIgnoreCase(product.getCategory(), q)
                    || containsIgnoreCase(product.getDescription(), q)
                    || (maker != null && containsIgnoreCase(maker.getName(), q));
            if (!textMatch) {
                return false;
            }
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            if (product.getCategory() == null || !product.getCategory().equalsIgnoreCase(filter.category().trim())) {
                return false;
            }
        }
        if (filter.makerGuid() != null && !filter.makerGuid().isBlank()) {
            if (maker == null || !filter.makerGuid().trim().equals(maker.getGuid())) {
                return false;
            }
        }
        if (filter.minPrice() != null) {
            if (product.getPrice() == null || product.getPrice().compareTo(filter.minPrice()) < 0) {
                return false;
            }
        }
        if (filter.maxPrice() != null) {
            if (product.getPrice() == null || product.getPrice().compareTo(filter.maxPrice()) > 0) {
                return false;
            }
        }
        if (filter.minRating() != null) {
            BigDecimal rating = product.getRatingAvg() != null ? product.getRatingAvg() : BigDecimal.ZERO;
            if (rating.compareTo(filter.minRating()) < 0) {
                return false;
            }
        }
        if (filter.materials() != null && !filter.materials().isEmpty()) {
            if (product.getMaterial() == null || !filter.materials().contains(product.getMaterial())) {
                return false;
            }
        }
        if (Boolean.TRUE.equals(filter.inStockOnly()) && !Boolean.TRUE.equals(product.getInStock())) {
            return false;
        }
        return true;
    }

    private Comparator<Product> buildComparator(Pageable pageable) {
        Comparator<Product> defaultComparator = Comparator
                .comparing(Product::getCreateDt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Product::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultComparator;
        }

        Comparator<Product> combined = null;
        for (Sort.Order order : pageable.getSort()) {
            Comparator<Product> next = switch (order.getProperty()) {
                case "price" -> Comparator.comparing(Product::getPrice, Comparator.nullsLast(BigDecimal::compareTo));
                case "rating", "ratingAvg" -> Comparator.comparing(
                        Product::getRatingAvg, Comparator.nullsLast(BigDecimal::compareTo));
                case "salesCount" -> Comparator.comparingInt(p -> p.getSalesCount() != null ? p.getSalesCount() : 0);
                case "reviewsCount" -> Comparator.comparingInt(p -> p.getReviewsCount() != null ? p.getReviewsCount() : 0);
                case "createdAt", "createDt" -> Comparator.comparing(
                        Product::getCreateDt, Comparator.nullsLast(Comparator.naturalOrder()));
                case "name" -> Comparator.comparing(Product::getName, Comparator.nullsLast(String::compareToIgnoreCase));
                default -> null;
            };
            if (next != null) {
                if (order.isDescending()) {
                    next = next.reversed();
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
