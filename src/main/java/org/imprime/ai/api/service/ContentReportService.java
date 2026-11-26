package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.report.CreateReportRequest;
import org.imprime.ai.api.http.request.report.CreateIpNoticeRequest;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.report.ReportResponse;
import org.springframework.data.domain.Pageable;

public interface ContentReportService {
    IpNoticeReceipt createIpNotice(CreateIpNoticeRequest request);

    IpNoticeReceipt getIpNotice(String trackingToken);

    record IpNoticeReceipt(String protocol, String trackingToken, ReportStatus status) {}

    ReportResponse createReport(CreateReportRequest request);

    PageResponse<ReportResponse> listMyReports(Pageable pageable);
}
