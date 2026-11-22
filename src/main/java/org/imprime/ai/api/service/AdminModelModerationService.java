package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.AdminDmcaTakedownRequest;
import org.imprime.ai.api.http.request.admin.AdminModerationReasonRequest;
import org.imprime.ai.api.http.request.admin.AdminOptionalNoteRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminModelDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminModelSummaryResponse;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.service.OrderManagementService;
import org.springframework.data.domain.Pageable;

public interface AdminModelModerationService {
    PageResponse<AdminModelSummaryResponse> listModels(
            ModelModerationStatus moderationStatus,
            String q,
            String category,
            String authorGuid,
            Pageable pageable
    );

    AdminModelDetailResponse getModelDetail(String modelGuid);

    OrderManagementService.OrderFileContent downloadModelFile(String modelGuid);

    AdminModelDetailResponse approveModel(String modelGuid, AdminOptionalNoteRequest request);

    AdminModelDetailResponse rejectModel(String modelGuid, AdminModerationReasonRequest request);

    AdminModelDetailResponse takedownModel(String modelGuid, AdminDmcaTakedownRequest request);

    AdminModelDetailResponse restoreModel(String modelGuid, AdminOptionalNoteRequest request);
}
