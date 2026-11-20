package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;

public interface OrderManagementService {
    OrderDetailResponse getOrderDetail(String orderGuid);

    OrderDetailResponse updateOrderStatus(String orderGuid, UpdateOrderStatusRequest request);
}
