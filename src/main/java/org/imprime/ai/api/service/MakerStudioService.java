package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.PatchMakerProfileRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProfileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.maker.MakerDashboardResponse;
import org.imprime.ai.api.http.response.maker.MakerStudioProfileResponse;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface MakerStudioService {
    MakerDashboardResponse getDashboard();

    PageResponse<OrderSummaryResponse> listMakerOrders(OrderStatusCd status, String query, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    MakerStudioProfileResponse getStudioProfile();

    MakerStudioProfileResponse replaceStudioProfile(UpdateMakerProfileRequest request);

    MakerStudioProfileResponse patchStudioProfile(PatchMakerProfileRequest request);
}
