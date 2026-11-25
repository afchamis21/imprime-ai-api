package org.imprime.ai.api.util;

import org.imprime.ai.api.http.response.common.DimensionsDto;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.maker.MakerSocialLinksDto;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.http.response.model3d.ModelAuthorResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerPortfolioItem;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CatalogDtoMapper {
    private CatalogDtoMapper() {
    }

    public static Map<Long, Address> indexPrimaryAddressesByCompanyId(List<Address> addresses) {
        Map<Long, Address> byCompany = new HashMap<>();
        if (addresses == null) {
            return byCompany;
        }
        for (Address address : addresses) {
            if (address == null || address.getOwnerId() == null) {
                continue;
            }
            Address existing = byCompany.get(address.getOwnerId());
            if (existing == null || Boolean.TRUE.equals(address.getDefaultAddress())) {
                byCompany.put(address.getOwnerId(), address);
            }
        }
        return byCompany;
    }

    public static PortfolioItemResponse toPortfolioItemResponse(MakerPortfolioItem item, JsonCodec jsonCodec) {
        List<String> imageUrls = jsonCodec.readStringList(item.getImageUrlsJson());
        String coverUrl = firstNonBlank(item.getCoverImageUrl(), imageUrls.isEmpty() ? null : imageUrls.getFirst());
        if (imageUrls.isEmpty() && coverUrl != null) {
            imageUrls = List.of(coverUrl);
        }
        return new PortfolioItemResponse(
                item.getGuid(),
                item.getTitle(),
                item.getMaterial(),
                coverUrl,
                imageUrls,
                item.getDescription(),
                item.getPrintTimeMinutes(),
                item.getPrinterUsed(),
                item.getLayerHeightMm(),
                jsonCodec.readStringList(item.getTagsJson()),
                item.getCreateDt()
        );
    }

    public static ProductCatalogItemResponse toProductCatalogItemResponse(
            Product product,
            MakerBriefResponse makerBrief,
            boolean favorited,
            JsonCodec jsonCodec) {
        String cover = resolveProductCoverUrl(product, jsonCodec);
        return new ProductCatalogItemResponse(
                product.getGuid(),
                product.getSlug(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getOldPrice(),
                product.getRatingAvg() != null ? product.getRatingAvg() : BigDecimal.ZERO,
                product.getReviewsCount() != null ? product.getReviewsCount() : 0,
                product.getMaterial(),
                product.getColorHex(),
                cover,
                Boolean.TRUE.equals(product.getInStock()),
                favorited,
                makerBrief,
                product.getCreateDt()
        );
    }

    public static String resolveProductCoverUrl(Product product, JsonCodec jsonCodec) {
        if (product.getCoverImageUrl() != null && !product.getCoverImageUrl().isBlank()) {
            return product.getCoverImageUrl();
        }
        List<String> images = jsonCodec.readStringList(product.getImageUrlsJson());
        return images.isEmpty() ? null : images.getFirst();
    }

    public static DimensionsDto toDimensionsDto(BigDecimal widthMm, BigDecimal depthMm, BigDecimal heightMm) {
        if (widthMm == null && depthMm == null && heightMm == null) {
            return null;
        }
        return new DimensionsDto(widthMm, depthMm, heightMm);
    }

    public static MakerSocialLinksDto readSocialLinks(Company company, JsonCodec jsonCodec) {
        MakerSocialLinksDto parsed = jsonCodec.readObject(company.getSocialLinksJson(), MakerSocialLinksDto.class);
        return parsed != null ? parsed : new MakerSocialLinksDto(null, null, null, null);
    }

    public static ModelAuthorResponse toModelAuthorResponse(User author, Company authorCompany) {
        if (authorCompany != null) {
            return new ModelAuthorResponse(
                    author != null ? author.getGuid() : authorCompany.getGuid(),
                    authorCompany.getSlug(),
                    firstNonBlank(authorCompany.getName(), author != null ? author.getFullName() : "Autor"),
                    firstNonBlank(authorCompany.getAvatarUrl(), author != null ? author.getAvatarUrl() : null),
                    Boolean.TRUE.equals(authorCompany.getVerified())
            );
        }
        if (author == null) {
            return new ModelAuthorResponse(null, null, "Designer", null, false);
        }
        return new ModelAuthorResponse(
                author.getGuid(),
                SlugUtil.slugify(author.getFullName()),
                author.getFullName(),
                author.getAvatarUrl(),
                false
        );
    }

    public static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    public static String trimToNull(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }
}
