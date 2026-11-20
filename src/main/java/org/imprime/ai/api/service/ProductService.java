package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.product.ProductCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.imprime.ai.api.http.response.product.ProductDetailResponse;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    PageResponse<ProductCatalogItemResponse> listProducts(ProductCatalogFilter filter, Pageable pageable);

    ProductDetailResponse getProductDetail(String slugOrGuid);

    FavoriteStatusResponse addFavorite(String productGuid);

    void removeFavorite(String productGuid);
}
