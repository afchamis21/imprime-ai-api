package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminApproveRefundRequest;
import org.imprime.ai.api.http.request.admin.AdminRejectRefundRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminRefundResponse;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.RefundType;
import org.imprime.ai.api.service.AdminRefundService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/refunds")
@RequireAdmin
@Tag(name = "Admin — Reembolsos", description = "Fila de aprovação e recusa de solicitações de reembolso total ou parcial")
public class AdminRefundController {
    private final AdminRefundService adminRefundService;

    @Operation(summary = "Listar solicitações de reembolso")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminRefundResponse>>> listRefunds(
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) RefundType refundType,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminRefundService.listRefunds(status, refundType, q, pageable));
    }

    @Operation(summary = "Aprovar e processar reembolso (total ou parcial)")
    @PostMapping("/{refundGuid}/approve")
    public ResponseEntity<BaseResponse<AdminRefundResponse>> approveRefund(
            @PathVariable String refundGuid,
            @Valid @RequestBody(required = false) AdminApproveRefundRequest request
    ) {
        return BaseResponse.ok(adminRefundService.approveRefund(refundGuid, request));
    }

    @Operation(summary = "Recusar solicitação de reembolso (motivo obrigatório)")
    @PostMapping("/{refundGuid}/reject")
    public ResponseEntity<BaseResponse<AdminRefundResponse>> rejectRefund(
            @PathVariable String refundGuid,
            @Valid @RequestBody AdminRejectRefundRequest request
    ) {
        return BaseResponse.ok(adminRefundService.rejectRefund(refundGuid, request));
    }
}
