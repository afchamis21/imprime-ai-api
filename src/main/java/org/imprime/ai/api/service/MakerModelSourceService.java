package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateModelSourceRequest;
import org.imprime.ai.api.http.request.maker.UpdateModelSourceRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.ModelSourceResponse;
import org.imprime.ai.api.http.response.maker.SyncModelSourceResponse;
import org.springframework.data.domain.Pageable;

public interface MakerModelSourceService {
    PageResponse<ModelSourceResponse> listSources(Pageable pageable);

    ModelSourceResponse createSource(CreateModelSourceRequest request);

    ModelSourceResponse updateSource(String sourceGuid, UpdateModelSourceRequest request);

    void deleteSource(String sourceGuid);

    SyncModelSourceResponse syncSource(String sourceGuid);
}
