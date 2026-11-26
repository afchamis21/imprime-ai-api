package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateMakerModelRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerModelResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MakerModelService {
    PageResponse<MakerModelResponse> listPublicMakerModels(String slugOrGuid, Pageable pageable);

    PageResponse<MakerModelResponse> listOwnModels(Pageable pageable);

    MakerModelResponse submitModel(MultipartFile file, CreateMakerModelRequest request);

    void deleteOwnModel(String modelGuid);

    void reorderModels(List<String> itemGuids);
}
