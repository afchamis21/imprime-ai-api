package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.CreatePortfolioItemRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.UpdatePortfolioItemRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerProductResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerStorefrontService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MakerStorefrontServiceImpl implements MakerStorefrontService {

    @Override
    public PageResponse<PortfolioItemResponse> listOwnPortfolio(Pageable pageable) {
        throw new NotImplementedException("MakerStorefrontService.listOwnPortfolio not yet implemented");
    }

    @Override
    public PortfolioItemResponse createPortfolioItem(CreatePortfolioItemRequest request) {
        throw new NotImplementedException("MakerStorefrontService.createPortfolioItem not yet implemented");
    }

    @Override
    public PortfolioItemResponse updatePortfolioItem(String itemGuid, UpdatePortfolioItemRequest request) {
        throw new NotImplementedException("MakerStorefrontService.updatePortfolioItem not yet implemented");
    }

    @Override
    public void deletePortfolioItem(String itemGuid) {
        throw new NotImplementedException("MakerStorefrontService.deletePortfolioItem not yet implemented");
    }

    @Override
    public PageResponse<MakerProductResponse> listOwnProducts(Pageable pageable) {
        throw new NotImplementedException("MakerStorefrontService.listOwnProducts not yet implemented");
    }

    @Override
    public MakerProductResponse createProduct(CreateMakerProductRequest request) {
        throw new NotImplementedException("MakerStorefrontService.createProduct not yet implemented");
    }

    @Override
    public MakerProductResponse updateProduct(String productGuid, UpdateMakerProductRequest request) {
        throw new NotImplementedException("MakerStorefrontService.updateProduct not yet implemented");
    }

    @Override
    public void deleteProduct(String productGuid) {
        throw new NotImplementedException("MakerStorefrontService.deleteProduct not yet implemented");
    }
}
