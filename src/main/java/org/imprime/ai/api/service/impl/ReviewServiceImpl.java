package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.Review;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.ReviewRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.ReviewService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.PageableSortMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private static final Map<String, String> SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "rating", "rating"
    );
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createDt");

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final Model3DRepository model3DRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Override
    public PageResponse<ReviewResponse> listReviews(
            ReviewTargetType targetType,
            String targetGuidOrSlug,
            Pageable pageable) {
        Long targetId = resolveTargetId(targetType, targetGuidOrSlug);
        Pageable mapped = PageableSortMapper.remap(pageable, SORT_MAPPING, DEFAULT_SORT);

        Page<Review> page = reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(
                targetType, targetId, StatusCd.ACTIVE, mapped
        );

        Set<Long> authorIds = page.getContent().stream()
                .map(Review::getAuthorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> authorsById = authorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Set<Long> companyIds = authorsById.values().stream()
                .map(User::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Company> companiesById = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        Page<ReviewResponse> mappedPage = page.map(review -> toReviewResponse(review, authorsById, companiesById));
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public ReviewResponse createReview(ReviewTargetType targetType, String targetGuid, CreateReviewRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Long targetId = resolveTargetId(targetType, targetGuid);

        if (reviewRepository.existsByAuthorUserIdAndTargetTypeAndTargetIdAndStatus(
                caller.getId(), targetType, targetId, StatusCd.ACTIVE)) {
            throw new ConflictException(MessageCd.REVIEW_ALREADY_EXISTS);
        }

        Review review = new Review();
        review.setAuthorUserId(caller.getId());
        review.setTargetType(targetType);
        review.setTargetId(targetId);
        review.setRating(request.rating());
        review.setComment(request.comment().trim());

        Review saved = reviewRepository.save(review);
        recalculateTargetRating(targetType, targetId);

        String avatarUrl = resolveAuthorAvatar(caller, null);

        return new ReviewResponse(
                saved.getGuid(),
                caller.getGuid(),
                caller.getFullName(),
                avatarUrl,
                saved.getRating(),
                saved.getComment(),
                saved.getCreateDt()
        );
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(
            ReviewTargetType targetType,
            String targetGuid,
            String reviewGuid,
            CreateReviewRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Long targetId = resolveTargetId(targetType, targetGuid);

        Review review = reviewRepository.findByGuidAndStatus(reviewGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.REVIEW_NOT_FOUND));

        if (review.getTargetType() != targetType || !Objects.equals(review.getTargetId(), targetId)) {
            throw new NotFoundException(MessageCd.REVIEW_NOT_FOUND);
        }

        if (!Objects.equals(review.getAuthorUserId(), caller.getId())) {
            throw new ForbiddenException();
        }

        review.setRating(request.rating());
        review.setComment(request.comment().trim());

        Review saved = reviewRepository.save(review);
        recalculateTargetRating(targetType, targetId);

        String avatarUrl = resolveAuthorAvatar(caller, null);

        return new ReviewResponse(
                saved.getGuid(),
                caller.getGuid(),
                caller.getFullName(),
                avatarUrl,
                saved.getRating(),
                saved.getComment(),
                saved.getCreateDt()
        );
    }

    @Override
    @Transactional
    public void deleteReview(ReviewTargetType targetType, String targetGuid, String reviewGuid) {
        User caller = ServiceContext.getUserOrThrow();
        Long targetId = resolveTargetId(targetType, targetGuid);

        Review review = reviewRepository.findByGuidAndStatus(reviewGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.REVIEW_NOT_FOUND));

        if (review.getTargetType() != targetType || !Objects.equals(review.getTargetId(), targetId)) {
            throw new NotFoundException(MessageCd.REVIEW_NOT_FOUND);
        }

        if (!Objects.equals(review.getAuthorUserId(), caller.getId())) {
            throw new ForbiddenException();
        }

        review.setStatus(StatusCd.INACTIVE);
        reviewRepository.save(review);
        recalculateTargetRating(targetType, targetId);
    }

    private Long resolveTargetId(ReviewTargetType targetType, String guidOrSlug) {
        return switch (targetType) {
            case PRODUCT -> productRepository.findByGuidAndStatus(guidOrSlug, StatusCd.ACTIVE)
                    .or(() -> productRepository.findBySlugAndStatus(guidOrSlug, StatusCd.ACTIVE))
                    .map(Product::getId)
                    .orElseThrow(() -> new NotFoundException(MessageCd.PRODUCT_NOT_FOUND));
            case MODEL_3D -> model3DRepository.findByGuidAndStatus(guidOrSlug, StatusCd.ACTIVE)
                    .or(() -> model3DRepository.findBySlugAndStatus(guidOrSlug, StatusCd.ACTIVE))
                    .map(Model3D::getId)
                    .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));
            case MAKER -> companyRepository.findByGuidAndStatus(guidOrSlug, StatusCd.ACTIVE)
                    .or(() -> companyRepository.findBySlugAndStatus(guidOrSlug, StatusCd.ACTIVE))
                    .map(Company::getId)
                    .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
        };
    }

    @Override
    public void recalculateTargetRating(ReviewTargetType targetType, Long targetId) {
        List<Review> activeReviews = reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(
                targetType, targetId, StatusCd.ACTIVE
        );
        int count = activeReviews.size();
        BigDecimal avg;
        if (count == 0) {
            avg = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            long totalStars = activeReviews.stream().mapToLong(Review::getRating).sum();
            avg = BigDecimal.valueOf(totalStars)
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }

        switch (targetType) {
            case PRODUCT -> productRepository.findById(targetId).ifPresent(product -> {
                product.setRatingAvg(avg);
                product.setReviewsCount(count);
                productRepository.save(product);
            });
            case MODEL_3D -> model3DRepository.findById(targetId).ifPresent(model -> {
                model.setRatingAvg(avg);
                model.setReviewsCount(count);
                model3DRepository.save(model);
            });
            case MAKER -> companyRepository.findById(targetId).ifPresent(company -> {
                company.setRatingAvg(avg);
                company.setReviewsCount(count);
                companyRepository.save(company);
            });
        }
    }

    private ReviewResponse toReviewResponse(
            Review review,
            Map<Long, User> authorsById,
            Map<Long, Company> companiesById) {
        User author = authorsById.get(review.getAuthorUserId());
        String authorGuid = author != null ? author.getGuid() : null;
        String authorName = author != null ? author.getFullName() : "Usuário";
        String avatarUrl = resolveAuthorAvatar(author, companiesById);

        return new ReviewResponse(
                review.getGuid(),
                authorGuid,
                authorName,
                avatarUrl,
                review.getRating(),
                review.getComment(),
                review.getCreateDt()
        );
    }

    private String resolveAuthorAvatar(User author, Map<Long, Company> companiesById) {
        if (author == null) {
            return null;
        }
        String companyAvatar = null;
        if (author.getCompanyId() != null) {
            companyAvatar = companiesById != null
                    ? companiesById.getOrDefault(author.getCompanyId(), new Company()).getAvatarUrl()
                    : companyRepository.findById(author.getCompanyId()).map(Company::getAvatarUrl).orElse(null);
        }
        return CatalogDtoMapper.firstNonBlank(companyAvatar, author.getAvatarUrl());
    }
}
