package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.report.CreateReportRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.report.ReportResponse;
import org.springframework.data.domain.Pageable;

public interface ContentReportService {
    ReportResponse createReport(CreateReportRequest request);

    PageResponse<ReportResponse> listMyReports(Pageable pageable);
}
