package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
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
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerProjectService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MakerProjectServiceImpl implements MakerProjectService {

    @Override
    public PageResponse<MakerProjectResponse> listProjects(ProjectStatus status, String query, Pageable pageable) {
        throw new NotImplementedException("MakerProjectService.listProjects not yet implemented");
    }

    @Override
    public MakerProjectResponse getProject(String projectGuid) {
        throw new NotImplementedException("MakerProjectService.getProject not yet implemented");
    }

    @Override
    public MakerProjectResponse createProject(CreateMakerProjectRequest request) {
        throw new NotImplementedException("MakerProjectService.createProject not yet implemented");
    }

    @Override
    public MakerProjectResponse updateProject(String projectGuid, UpdateMakerProjectRequest request) {
        throw new NotImplementedException("MakerProjectService.updateProject not yet implemented");
    }

    @Override
    public void deleteProject(String projectGuid) {
        throw new NotImplementedException("MakerProjectService.deleteProject not yet implemented");
    }

    @Override
    public BomItemResponse addBomItem(String projectGuid, CreateBomItemRequest request) {
        throw new NotImplementedException("MakerProjectService.addBomItem not yet implemented");
    }

    @Override
    public BomItemResponse updateBomItem(String projectGuid, String bomItemGuid, UpdateBomItemRequest request) {
        throw new NotImplementedException("MakerProjectService.updateBomItem not yet implemented");
    }

    @Override
    public void deleteBomItem(String projectGuid, String bomItemGuid) {
        throw new NotImplementedException("MakerProjectService.deleteBomItem not yet implemented");
    }

    @Override
    public PrintQueueItemResponse queueNextPlate(String projectGuid, QueueNextPlateRequest request) {
        throw new NotImplementedException("MakerProjectService.queueNextPlate not yet implemented");
    }
}
