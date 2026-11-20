package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreateModelSourceRequest;
import org.imprime.ai.api.http.request.maker.UpdateModelSourceRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.ModelSourceResponse;
import org.imprime.ai.api.http.response.maker.SyncModelSourceResponse;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerModelSourceService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MakerModelSourceServiceImpl implements MakerModelSourceService {

    @Override
    public PageResponse<ModelSourceResponse> listSources(Pageable pageable) {
        throw new NotImplementedException("MakerModelSourceService.listSources not yet implemented");
    }

    @Override
    public ModelSourceResponse createSource(CreateModelSourceRequest request) {
        throw new NotImplementedException("MakerModelSourceService.createSource not yet implemented");
    }

    @Override
    public ModelSourceResponse updateSource(String sourceGuid, UpdateModelSourceRequest request) {
        throw new NotImplementedException("MakerModelSourceService.updateSource not yet implemented");
    }

    @Override
    public void deleteSource(String sourceGuid) {
        throw new NotImplementedException("MakerModelSourceService.deleteSource not yet implemented");
    }

    @Override
    public SyncModelSourceResponse syncSource(String sourceGuid) {
        throw new NotImplementedException("MakerModelSourceService.syncSource not yet implemented");
    }
}
