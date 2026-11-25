package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminUpdateReportRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminReportDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminReportSummaryResponse;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.imprime.ai.api.service.AdminReportService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reports")
@RequireAdmin
@Tag(name = "Admin — Denúncias", description = "Triagem, análise de evidências e resolução de denúncias sobre produtos, modelos 3D e makers")
public class AdminReportController {
    private final AdminReportService adminReportService;

    @Operation(summary = "Listar denúncias da plataforma com filtros por tipo de alvo, status, motivo e prioridade")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminReportSummaryResponse>>> listReports(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) ReportStatus reportStatus,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String assignedAdminGuid,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminReportService.listReports(
                q, targetType, reportStatus, reason, priority, assignedAdminGuid, pageable
        ));
    }

    @Operation(summary = "Obter detalhes completos de uma denúncia")
    @GetMapping("/{reportGuid}")
    public ResponseEntity<BaseResponse<AdminReportDetailResponse>> getReportDetail(
            @PathVariable String reportGuid
    ) {
        return BaseResponse.ok(adminReportService.getReportDetail(reportGuid));
    }

    @Operation(summary = "Atualizar status, prioridade, responsável e parecer de uma denúncia")
    @PatchMapping("/{reportGuid}")
    public ResponseEntity<BaseResponse<AdminReportDetailResponse>> updateReport(
            @PathVariable String reportGuid,
            @Valid @RequestBody AdminUpdateReportRequest request
    ) {
        return BaseResponse.ok(adminReportService.updateReport(reportGuid, request));
    }
}
