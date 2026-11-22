package org.imprime.ai.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.admin.AdminCancelOrderRequest;
import org.imprime.ai.api.http.request.admin.AdminCreateRefundRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminRefundResponse;
import org.imprime.ai.api.model.enums.AdminOrderType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.service.AdminOrderService;
import org.imprime.ai.api.service.AdminRefundService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
@RequireAdmin
@Tag(name = "Admin — Pedidos", description = "Gestão global de pedidos digitais e físicos, cancelamentos administrativos e abertura de estornos")
public class AdminOrderController {
    private final AdminOrderService adminOrderService;
    private final AdminRefundService adminRefundService;

    @Operation(summary = "Listar todos os pedidos da plataforma")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminOrderSummaryResponse>>> listOrders(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatusCd status,
            @RequestParam(required = false) AdminOrderType orderType,
            @RequestParam(required = false) Boolean slaDelayedOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return BaseResponse.ok(adminOrderService.listOrders(q, status, orderType, slaDelayedOnly, fromDate, toDate, pageable));
    }

    @Operation(summary = "Obter detalhamento administrativo completo de um pedido")
    @GetMapping("/{orderGuid}")
    public ResponseEntity<BaseResponse<AdminOrderDetailResponse>> getOrderDetail(@PathVariable String orderGuid) {
        return BaseResponse.ok(adminOrderService.getOrderDetail(orderGuid));
    }

    @Operation(summary = "Cancelar pedido administrativamente (motivo obrigatório)")
    @PostMapping("/{orderGuid}/cancel")
    public ResponseEntity<BaseResponse<AdminOrderDetailResponse>> cancelOrder(
            @PathVariable String orderGuid,
            @Valid @RequestBody AdminCancelOrderRequest request
    ) {
        return BaseResponse.ok(adminOrderService.cancelOrder(orderGuid, request));
    }

    @Operation(summary = "Criar ou processar reembolso total/parcial para o pedido")
    @PostMapping("/{orderGuid}/refunds")
    public ResponseEntity<BaseResponse<AdminRefundResponse>> createOrderRefund(
            @PathVariable String orderGuid,
            @Valid @RequestBody AdminCreateRefundRequest request
    ) {
        return BaseResponse.created(adminRefundService.createRefund(orderGuid, request));
    }
}
