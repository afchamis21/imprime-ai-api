package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.user.UpdateUserProfileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.user.ModelDownloadResponse;
import org.imprime.ai.api.http.response.user.PurchasedModelResponse;
import org.imprime.ai.api.http.response.user.UserReviewResponse;
import org.imprime.ai.api.model.dto.FullUserDTO;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.UserHubService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserHubServiceImpl implements UserHubService {

    @Override
    public FullUserDTO updateProfile(UpdateUserProfileRequest request) {
        throw new NotImplementedException("UserHubService.updateProfile not yet implemented");
    }

    @Override
    public PageResponse<OrderSummaryResponse> listUserOrders(OrderStatusCd status, String query, Pageable pageable) {
        throw new NotImplementedException("UserHubService.listUserOrders not yet implemented");
    }

    @Override
    public PageResponse<PurchasedModelResponse> listPurchasedModels(String query, FileFormat3D format, Boolean customizableOnly, Pageable pageable) {
        throw new NotImplementedException("UserHubService.listPurchasedModels not yet implemented");
    }

    @Override
    public ModelDownloadResponse generatePurchasedModelDownloadUrl(String purchaseGuid) {
        throw new NotImplementedException("UserHubService.generatePurchasedModelDownloadUrl not yet implemented");
    }

    @Override
    public PageResponse<UserReviewResponse> listUserReviews(ReviewTargetType targetType, Pageable pageable) {
        throw new NotImplementedException("UserHubService.listUserReviews not yet implemented");
    }
}
