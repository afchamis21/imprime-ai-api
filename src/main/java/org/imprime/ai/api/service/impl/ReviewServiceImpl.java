package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.ReviewService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    @Override
    public PageResponse<ReviewResponse> listReviews(ReviewTargetType targetType, String targetGuidOrSlug, Pageable pageable) {
        throw new NotImplementedException("ReviewService.listReviews not yet implemented");
    }

    @Override
    public ReviewResponse createReview(ReviewTargetType targetType, String targetGuid, CreateReviewRequest request) {
        throw new NotImplementedException("ReviewService.createReview not yet implemented");
    }

    @Override
    public void deleteReview(ReviewTargetType targetType, String targetGuid, String reviewGuid) {
        throw new NotImplementedException("ReviewService.deleteReview not yet implemented");
    }
}
