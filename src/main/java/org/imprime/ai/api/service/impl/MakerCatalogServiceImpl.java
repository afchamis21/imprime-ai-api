package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.MakerCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.maker.MakerCatalogItemResponse;
import org.imprime.ai.api.http.response.maker.MakerProfileResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.http.response.product.ProductCatalogItemResponse;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerCatalogService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MakerCatalogServiceImpl implements MakerCatalogService {

    @Override
    public PageResponse<MakerCatalogItemResponse> listMakers(MakerCatalogFilter filter, Pageable pageable) {
        throw new NotImplementedException("MakerCatalogService.listMakers not yet implemented");
    }

    @Override
    public MakerProfileResponse getMakerProfile(String slugOrGuid) {
        throw new NotImplementedException("MakerCatalogService.getMakerProfile not yet implemented");
    }

    @Override
    public PageResponse<PortfolioItemResponse> listMakerPortfolio(String slugOrGuid, Pageable pageable) {
        throw new NotImplementedException("MakerCatalogService.listMakerPortfolio not yet implemented");
    }

    @Override
    public PageResponse<ProductCatalogItemResponse> listMakerProducts(String slugOrGuid, Pageable pageable) {
        throw new NotImplementedException("MakerCatalogService.listMakerProducts not yet implemented");
    }

    @Override
    public FavoriteStatusResponse addFavorite(String makerGuid) {
        throw new NotImplementedException("MakerCatalogService.addFavorite not yet implemented");
    }

    @Override
    public void removeFavorite(String makerGuid) {
        throw new NotImplementedException("MakerCatalogService.removeFavorite not yet implemented");
    }
}
