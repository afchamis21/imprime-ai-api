package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.model3d.Model3DCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.http.response.model3d.Model3DCatalogItemResponse;
import org.imprime.ai.api.http.response.model3d.Model3DDetailResponse;
import org.imprime.ai.api.http.response.model3d.ModelCustomizationResponse;
import org.springframework.data.domain.Pageable;

public interface Model3DService {
    PageResponse<Model3DCatalogItemResponse> listModels(Model3DCatalogFilter filter, Pageable pageable);

    Model3DDetailResponse getModelDetail(String slugOrGuid);

    FavoriteStatusResponse addFavorite(String modelGuid);

    void removeFavorite(String modelGuid);

    ModelCustomizationResponse getCustomization(String slugOrGuid);
}
