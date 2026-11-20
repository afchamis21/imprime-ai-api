package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.OrderManagementService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderManagementServiceImpl implements OrderManagementService {

    @Override
    public OrderDetailResponse getOrderDetail(String orderGuid) {
        throw new NotImplementedException("OrderManagementService.getOrderDetail not yet implemented");
    }

    @Override
    public OrderDetailResponse updateOrderStatus(String orderGuid, UpdateOrderStatusRequest request) {
        throw new NotImplementedException("OrderManagementService.updateOrderStatus not yet implemented");
    }
}
