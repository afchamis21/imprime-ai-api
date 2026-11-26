package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.CreatePortfolioItemRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.UpdatePortfolioItemRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerProductResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MakerStorefrontService {
    PageResponse<PortfolioItemResponse> listOwnPortfolio(Pageable pageable);

    PortfolioItemResponse createPortfolioItem(CreatePortfolioItemRequest request);

    PortfolioItemResponse updatePortfolioItem(String itemGuid, UpdatePortfolioItemRequest request);

    void deletePortfolioItem(String itemGuid);

    void reorderPortfolio(List<String> itemGuids);

    PageResponse<MakerProductResponse> listOwnProducts(Pageable pageable);

    MakerProductResponse createProduct(CreateMakerProductRequest request);

    MakerProductResponse updateProduct(String productGuid, UpdateMakerProductRequest request);

    void deleteProduct(String productGuid);

    void reorderProducts(List<String> itemGuids);
}
