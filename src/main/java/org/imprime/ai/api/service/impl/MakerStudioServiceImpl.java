package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.PatchMakerProfileRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProfileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.maker.MakerDashboardResponse;
import org.imprime.ai.api.http.response.maker.MakerStudioProfileResponse;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.MakerStudioService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MakerStudioServiceImpl implements MakerStudioService {

    @Override
    public MakerDashboardResponse getDashboard() {
        throw new NotImplementedException("MakerStudioService.getDashboard not yet implemented");
    }

    @Override
    public PageResponse<OrderSummaryResponse> listMakerOrders(OrderStatusCd status, String query, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        throw new NotImplementedException("MakerStudioService.listMakerOrders not yet implemented");
    }

    @Override
    public MakerStudioProfileResponse getStudioProfile() {
        throw new NotImplementedException("MakerStudioService.getStudioProfile not yet implemented");
    }

    @Override
    public MakerStudioProfileResponse replaceStudioProfile(UpdateMakerProfileRequest request) {
        throw new NotImplementedException("MakerStudioService.replaceStudioProfile not yet implemented");
    }

    @Override
    public MakerStudioProfileResponse patchStudioProfile(PatchMakerProfileRequest request) {
        throw new NotImplementedException("MakerStudioService.patchStudioProfile not yet implemented");
    }
}
