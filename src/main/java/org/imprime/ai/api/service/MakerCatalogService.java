package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.MakerCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.maker.MakerCatalogItemResponse;
import org.imprime.ai.api.http.response.maker.MakerProfileResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.springframework.data.domain.Pageable;

public interface MakerCatalogService {
    PageResponse<MakerCatalogItemResponse> listMakers(MakerCatalogFilter filter, Pageable pageable);

    MakerProfileResponse getMakerProfile(String slugOrGuid);

    PageResponse<PortfolioItemResponse> listMakerPortfolio(String slugOrGuid, Pageable pageable);

    PageResponse<ProductCatalogItemResponse> listMakerProducts(String slugOrGuid, Pageable pageable);

    FavoriteStatusResponse addFavorite(String makerGuid);

    void removeFavorite(String makerGuid);
}
