package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.AdminMakerApproveRequest;
import org.imprime.ai.api.http.request.admin.AdminMakerStatusRequest;
import org.imprime.ai.api.http.request.admin.AdminModerationReasonRequest;
import org.imprime.ai.api.http.request.admin.AdminProductStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminCatalogProductResponse;
import org.imprime.ai.api.http.response.admin.AdminMakerDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminMakerSummaryResponse;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Pageable;

public interface AdminMakerCatalogService {
    PageResponse<AdminMakerSummaryResponse> listMakers(
            MakerApprovalStatus approvalStatus,
            Boolean verified,
            Boolean online,
            String q,
            Pageable pageable
    );

    AdminMakerDetailResponse getMakerDetail(String companyGuid);

    AdminMakerDetailResponse approveMaker(String companyGuid, AdminMakerApproveRequest request);

    AdminMakerDetailResponse rejectMaker(String companyGuid, AdminModerationReasonRequest request);

    AdminMakerDetailResponse updateMakerStatus(String companyGuid, AdminMakerStatusRequest request);

    PageResponse<AdminCatalogProductResponse> listCatalogProducts(
            String q,
            String category,
            String makerGuid,
            StatusCd status,
            Boolean inStock,
            Pageable pageable
    );

    AdminCatalogProductResponse updateProductStatus(String productGuid, AdminProductStatusRequest request);
}
