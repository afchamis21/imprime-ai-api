package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.AdminPayoutCompleteRequest;
import org.imprime.ai.api.http.request.admin.AdminPayoutFailRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminEscrowItemResponse;
import org.imprime.ai.api.http.response.admin.AdminFinanceSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminPayoutItemResponse;
import org.imprime.ai.api.model.enums.AdminPayoutBeneficiaryType;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface AdminFinanceService {
    AdminFinanceSummaryResponse getSummary();

    PageResponse<AdminPayoutItemResponse> listPayouts(
            AdminPayoutBeneficiaryType beneficiaryType,
            FinanceTransactionStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String q,
            Pageable pageable
    );

    AdminPayoutItemResponse completePayout(String payoutGuid, AdminPayoutCompleteRequest request);

    AdminPayoutItemResponse failPayout(String payoutGuid, AdminPayoutFailRequest request);

    PageResponse<AdminEscrowItemResponse> listEscrow(
            String q,
            OrderStatusCd orderStatus,
            Boolean disputedOnly,
            Pageable pageable
    );
}
