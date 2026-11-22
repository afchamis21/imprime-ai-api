package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminAuditLogDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminAuditLogSummaryResponse;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface AdminAuditService {
    void record(
            AuditAction action,
            String targetType,
            String targetGuid,
            String reason,
            Object beforeState,
            Object afterState
    );

    void recordForActor(
            User actor,
            AuditAction action,
            String targetType,
            String targetGuid,
            String reason,
            Object beforeState,
            Object afterState
    );

    PageResponse<AdminAuditLogSummaryResponse> listAuditLogs(
            String adminGuid,
            AuditAction action,
            String targetType,
            String targetGuid,
            LocalDate fromDate,
            LocalDate toDate,
            String q,
            Pageable pageable
    );

    AdminAuditLogDetailResponse getAuditLogDetail(String logGuid);
}
