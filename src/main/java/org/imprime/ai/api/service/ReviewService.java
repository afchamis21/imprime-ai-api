package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.common.CreateMakerReviewRequest;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    PageResponse<ReviewResponse> listReviews(ReviewTargetType targetType, String targetGuidOrSlug, Pageable pageable);

    ReviewResponse createReview(ReviewTargetType targetType, String targetGuid, CreateReviewRequest request);

    ReviewResponse createOrderMakerReview(String orderGuid, CreateReviewRequest request);

    ReviewResponse createMakerReview(String makerGuidOrSlug, CreateMakerReviewRequest request);

    ReviewResponse updateReview(ReviewTargetType targetType, String targetGuid, String reviewGuid, CreateReviewRequest request);

    void deleteReview(ReviewTargetType targetType, String targetGuid, String reviewGuid);

    void recalculateTargetRating(ReviewTargetType targetType, Long targetId);
}
