package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.user.UpdateUserAvatarRequest;
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
import org.springframework.data.domain.Pageable;

public interface UserHubService {
    FullUserDTO updateProfile(UpdateUserProfileRequest request);

    FullUserDTO updateAvatar(UpdateUserAvatarRequest request);

    PageResponse<OrderSummaryResponse> listUserOrders(OrderStatusCd status, String query, Pageable pageable);

    PageResponse<PurchasedModelResponse> listPurchasedModels(String query, FileFormat3D format, Boolean customizableOnly, Pageable pageable);

    ModelDownloadResponse generatePurchasedModelDownloadUrl(String purchaseGuid);

    PageResponse<UserReviewResponse> listUserReviews(ReviewTargetType targetType, Pageable pageable);

    UserReviewResponse updateUserReview(String reviewGuid, CreateReviewRequest request);

    void deleteUserReview(String reviewGuid);
}
