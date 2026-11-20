package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.product.ProductCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.imprime.ai.api.http.response.product.ProductDetailResponse;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    @Override
    public PageResponse<ProductCatalogItemResponse> listProducts(ProductCatalogFilter filter, Pageable pageable) {
        throw new NotImplementedException("ProductService.listProducts not yet implemented");
    }

    @Override
    public ProductDetailResponse getProductDetail(String slugOrGuid) {
        throw new NotImplementedException("ProductService.getProductDetail not yet implemented");
    }

    @Override
    public FavoriteStatusResponse addFavorite(String productGuid) {
        throw new NotImplementedException("ProductService.addFavorite not yet implemented");
    }

    @Override
    public void removeFavorite(String productGuid) {
        throw new NotImplementedException("ProductService.removeFavorite not yet implemented");
    }
}
