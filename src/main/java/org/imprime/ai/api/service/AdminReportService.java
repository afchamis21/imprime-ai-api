package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.AdminUpdateReportRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminReportDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminReportSummaryResponse;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.springframework.data.domain.Pageable;

public interface AdminReportService {
    PageResponse<AdminReportSummaryResponse> listReports(
            String q,
            ReportTargetType targetType,
            ReportStatus reportStatus,
            ReportReason reason,
            TicketPriority priority,
            String assignedAdminGuid,
            Pageable pageable
    );

    AdminReportDetailResponse getReportDetail(String reportGuid);

    AdminReportDetailResponse updateReport(String reportGuid, AdminUpdateReportRequest request);
}
