package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminDashboardSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminSlaAlertResponse;
import org.springframework.data.domain.Pageable;

public interface AdminDashboardService {
    AdminDashboardSummaryResponse getSummary(String range);

    PageResponse<AdminSlaAlertResponse> listSlaAlerts(String severity, Pageable pageable);
}
