package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminPayoutCompleteRequest;
import org.imprime.ai.api.http.request.admin.AdminPayoutFailRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminEscrowItemResponse;
import org.imprime.ai.api.http.response.admin.AdminFinanceSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminPayoutItemResponse;
import org.imprime.ai.api.model.enums.AdminPayoutBeneficiaryType;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.service.AdminFinanceService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/finance")
@RequireAdmin
@Tag(name = "Admin — Financeiro Unificado", description = "Consolidado de taxas, saldo retido em escrow e repasses (payouts) para Makers e Designers")
public class AdminFinanceController {
    private final AdminFinanceService adminFinanceService;

    @Operation(summary = "Obter resumo financeiro consolidado da plataforma")
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<AdminFinanceSummaryResponse>> getSummary() {
        return BaseResponse.ok(adminFinanceService.getSummary());
    }

    @Operation(summary = "Listar repasses (payouts) de Makers e Designers")
    @GetMapping("/payouts")
    public ResponseEntity<BaseResponse<PageResponse<AdminPayoutItemResponse>>> listPayouts(
            @RequestParam(required = false) AdminPayoutBeneficiaryType beneficiaryType,
            @RequestParam(required = false) FinanceTransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminFinanceService.listPayouts(beneficiaryType, status, fromDate, toDate, q, pageable));
    }

    @Operation(summary = "Marcar repasse pendente/em processamento como concluído")
    @PostMapping("/payouts/{payoutGuid}/complete")
    public ResponseEntity<BaseResponse<AdminPayoutItemResponse>> completePayout(
            @PathVariable String payoutGuid,
            @RequestBody(required = false) @Valid AdminPayoutCompleteRequest request
    ) {
        return BaseResponse.ok(adminFinanceService.completePayout(payoutGuid, request));
    }

    @Operation(summary = "Reprovar ou registrar falha em repasse (motivo obrigatório)")
    @PostMapping("/payouts/{payoutGuid}/fail")
    public ResponseEntity<BaseResponse<AdminPayoutItemResponse>> failPayout(
            @PathVariable String payoutGuid,
            @Valid @RequestBody AdminPayoutFailRequest request
    ) {
        return BaseResponse.ok(adminFinanceService.failPayout(payoutGuid, request));
    }

    @Operation(summary = "Listar pedidos com saldo retido em custódia (escrow)")
    @GetMapping("/escrow")
    public ResponseEntity<BaseResponse<PageResponse<AdminEscrowItemResponse>>> listEscrow(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatusCd orderStatus,
            @RequestParam(required = false) Boolean disputedOnly,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminFinanceService.listEscrow(q, orderStatus, disputedOnly, pageable));
    }
}
