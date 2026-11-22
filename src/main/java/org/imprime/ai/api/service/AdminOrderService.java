package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.AdminCancelOrderRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderSummaryResponse;
import org.imprime.ai.api.model.enums.AdminOrderType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface AdminOrderService {
    PageResponse<AdminOrderSummaryResponse> listOrders(
            String q,
            OrderStatusCd status,
            AdminOrderType orderType,
            Boolean slaDelayedOnly,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );

    AdminOrderDetailResponse getOrderDetail(String orderGuid);

    AdminOrderDetailResponse cancelOrder(String orderGuid, AdminCancelOrderRequest request);
}
