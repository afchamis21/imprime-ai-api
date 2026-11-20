package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.model3d.Model3DCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.model3d.Model3DCatalogItemResponse;
import org.imprime.ai.api.http.response.model3d.Model3DDetailResponse;
import org.imprime.ai.api.http.response.model3d.ModelCustomizationResponse;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.Model3DService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Model3DServiceImpl implements Model3DService {

    @Override
    public PageResponse<Model3DCatalogItemResponse> listModels(Model3DCatalogFilter filter, Pageable pageable) {
        throw new NotImplementedException("Model3DService.listModels not yet implemented");
    }

    @Override
    public Model3DDetailResponse getModelDetail(String slugOrGuid) {
        throw new NotImplementedException("Model3DService.getModelDetail not yet implemented");
    }

    @Override
    public FavoriteStatusResponse addFavorite(String modelGuid) {
        throw new NotImplementedException("Model3DService.addFavorite not yet implemented");
    }

    @Override
    public void removeFavorite(String modelGuid) {
        throw new NotImplementedException("Model3DService.removeFavorite not yet implemented");
    }

    @Override
    public ModelCustomizationResponse getCustomization(String slugOrGuid) {
        throw new NotImplementedException("Model3DService.getCustomization not yet implemented");
    }
}
