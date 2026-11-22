package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.AdminApproveRefundRequest;
import org.imprime.ai.api.http.request.admin.AdminCreateRefundRequest;
import org.imprime.ai.api.http.request.admin.AdminRejectRefundRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminRefundResponse;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.RefundType;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminRefundService {
    PageResponse<AdminRefundResponse> listRefunds(
            RefundStatus status,
            RefundType refundType,
            String q,
            Pageable pageable
    );

    List<AdminRefundResponse> listRefundsForOrder(Long orderId);

    AdminRefundResponse createRefund(String orderGuid, AdminCreateRefundRequest request);

    AdminRefundResponse approveRefund(String refundGuid, AdminApproveRefundRequest request);

    AdminRefundResponse rejectRefund(String refundGuid, AdminRejectRefundRequest request);
}
