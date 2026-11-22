package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.http.response.order.OrderFileResponse;

import java.util.List;

public interface OrderManagementService {
    OrderDetailResponse getOrderDetail(String orderGuid);

    OrderDetailResponse updateOrderStatus(String orderGuid, UpdateOrderStatusRequest request);

    List<OrderFileResponse> listOrderFiles(String orderGuid);

    OrderFileContent downloadOrderFile(String orderGuid, String fileGuid);

    record OrderFileContent(String fileName, String contentType, byte[] bytes) {
    }
}
