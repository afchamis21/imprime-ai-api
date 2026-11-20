package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateBomItemRequest;
import org.imprime.ai.api.http.request.maker.CreateMakerProjectRequest;
import org.imprime.ai.api.http.request.maker.QueueNextPlateRequest;
import org.imprime.ai.api.http.request.maker.UpdateBomItemRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProjectRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.BomItemResponse;
import org.imprime.ai.api.http.response.maker.MakerProjectResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.ProjectStatus;
import org.springframework.data.domain.Pageable;

public interface MakerProjectService {
    PageResponse<MakerProjectResponse> listProjects(ProjectStatus status, String query, Pageable pageable);

    MakerProjectResponse getProject(String projectGuid);

    MakerProjectResponse createProject(CreateMakerProjectRequest request);

    MakerProjectResponse updateProject(String projectGuid, UpdateMakerProjectRequest request);

    void deleteProject(String projectGuid);

    BomItemResponse addBomItem(String projectGuid, CreateBomItemRequest request);

    BomItemResponse updateBomItem(String projectGuid, String bomItemGuid, UpdateBomItemRequest request);

    void deleteBomItem(String projectGuid, String bomItemGuid);

    PrintQueueItemResponse queueNextPlate(String projectGuid, QueueNextPlateRequest request);
}
