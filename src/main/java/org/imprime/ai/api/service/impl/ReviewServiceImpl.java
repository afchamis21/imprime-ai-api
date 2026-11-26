package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.common.CreateMakerReviewRequest;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.common.CreateReviewReplyRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.http.response.common.ReviewLikeResponse;
import org.imprime.ai.api.http.response.common.ReviewReplyResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.Review;
import org.imprime.ai.api.model.ReviewReply;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.ReviewRepository;
import org.imprime.ai.api.repo.db.ReviewReplyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.ReviewService;
import org.imprime.ai.api.service.ObjectStorageService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.PageableSortMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final ReviewReplyRepository reviewReplyRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ObjectStorageService objectStorageService;
    private final JdbcClient jdbc;

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

        boolean catalogReviews = targetType != ReviewTargetType.MAKER && page.hasContent();
        List<Long> reviewIds = page.getContent().stream().map(Review::getId).toList();
        List<ReviewReply> replies = catalogReviews
                ? reviewReplyRepository.findAllByReviewIdInAndStatusOrderByCreateDtAscIdAsc(reviewIds, StatusCd.ACTIVE)
                : List.of();
        Map<Long, ReviewLikeResponse> likes = catalogReviews ? readLikes(reviewIds) : Map.of();
        Set<Long> authorIds = Stream.concat(
                        page.getContent().stream().map(Review::getAuthorUserId),
                        replies.stream().map(ReviewReply::getAuthorUserId))
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

        Map<Long, List<ReviewReplyResponse>> repliesByReview = replies.stream().collect(Collectors.groupingBy(
                ReviewReply::getReviewId, Collectors.mapping(
                        reply -> toReplyResponse(reply, authorsById.get(reply.getAuthorUserId()), companiesById),
                        Collectors.toList())));
        Page<ReviewResponse> mappedPage = page.map(review -> toReviewResponse(review,
                authorsById.get(review.getAuthorUserId()), companiesById,
                likes.getOrDefault(review.getId(), new ReviewLikeResponse(0, false)),
                repliesByReview.getOrDefault(review.getId(), List.of())));
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public ReviewResponse createReview(ReviewTargetType targetType, String targetGuid, CreateReviewRequest request) {
        if (targetType == ReviewTargetType.MAKER) {
            return createMakerReview(
                    targetGuid,
                    new CreateMakerReviewRequest(request.rating(), request.comment(), null)
            );
        }
        User caller = ServiceContext.getUserOrThrow();
        Long targetId = resolveTargetId(targetType, targetGuid);

        if (reviewRepository.existsByAuthorUserIdAndTargetTypeAndTargetIdAndStatus(
                caller.getId(), targetType, targetId, StatusCd.ACTIVE)) {
            throw new ConflictException(MessageCd.REVIEW_ALREADY_EXISTS);
        }

        return saveAndRespond(caller, targetType, targetId, null, request.rating(), request.comment(),
                resolvePhotos(caller, request.photoGuids()));
    }

    @Override
    @Transactional
    public ReviewResponse createOrderMakerReview(String orderGuid, CreateReviewRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findOrderByGuid(orderGuid)
                .filter(o -> StatusCd.ACTIVE.equals(o.getStatus()))
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));

        if (!Objects.equals(caller.getId(), order.getBuyerId())) {
            throw new ForbiddenException();
        }
        if (order.getVendorId() == null) {
            throw new NotFoundException(MessageCd.MAKER_NOT_FOUND);
        }
        companyRepository.findById(order.getVendorId())
                .filter(c -> StatusCd.ACTIVE.equals(c.getStatus()))
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));

        if (!OrderStatusCd.COMPLETED.equals(orderStatusService.findLatest(order).getStatusCd())) {
            throw new BadRequestException(MessageCd.REVIEW_ORDER_NOT_ELIGIBLE);
        }
        if (reviewRepository.existsByAuthorUserIdAndTargetTypeAndOrderIdAndStatus(
                caller.getId(), ReviewTargetType.MAKER, order.getId(), StatusCd.ACTIVE)) {
            throw new ConflictException(MessageCd.REVIEW_ALREADY_EXISTS);
        }

        return saveAndRespond(caller, ReviewTargetType.MAKER, order.getVendorId(), order.getId(),
                request.rating(), request.comment(), List.of());
    }

    @Override
    @Transactional
    public ReviewResponse createMakerReview(String makerGuidOrSlug, CreateMakerReviewRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Long companyId = resolveTargetId(ReviewTargetType.MAKER, makerGuidOrSlug);

        if (request.orderGuid() != null && !request.orderGuid().isBlank()) {
            String orderGuid = request.orderGuid().trim();
            Order order = orderRepository.findOrderByGuid(orderGuid)
                    .filter(o -> StatusCd.ACTIVE.equals(o.getStatus()))
                    .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
            if (!Objects.equals(order.getVendorId(), companyId)) {
                throw new BadRequestException(MessageCd.REVIEW_ORDER_NOT_ELIGIBLE);
            }
            return createOrderMakerReview(
                    order.getGuid(),
                    new CreateReviewRequest(request.rating(), request.comment())
            );
        }

        List<Order> buyerOrders = orderRepository.findAllByBuyerIdAndVendorIdAndStatusOrderByCreateDtDesc(
                caller.getId(), companyId, StatusCd.ACTIVE
        );
        List<Order> completedOrders = buyerOrders.stream()
                .filter(o -> OrderStatusCd.COMPLETED.equals(orderStatusService.findLatest(o).getStatusCd()))
                .toList();
        if (completedOrders.isEmpty()) {
            throw new BadRequestException(MessageCd.REVIEW_ORDER_NOT_ELIGIBLE);
        }

        Set<Long> reviewedOrderIds = reviewRepository
                .findAllByAuthorUserIdAndTargetTypeAndTargetIdAndStatus(
                        caller.getId(), ReviewTargetType.MAKER, companyId, StatusCd.ACTIVE
                )
                .stream()
                .map(Review::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Order chosenOrder = completedOrders.stream()
                .filter(o -> !reviewedOrderIds.contains(o.getId()))
                .findFirst()
                .orElseThrow(() -> new ConflictException(MessageCd.REVIEW_ALREADY_EXISTS));

        return saveAndRespond(caller, ReviewTargetType.MAKER, companyId, chosenOrder.getId(),
                request.rating(), request.comment(), List.of());
    }

    private ReviewResponse saveAndRespond(
            User caller,
            ReviewTargetType targetType,
            Long targetId,
            Long orderId,
            Integer rating,
            String comment,
            List<String> imageUrls) {
        Review review = new Review();
        review.setAuthorUserId(caller.getId());
        review.setTargetType(targetType);
        review.setTargetId(targetId);
        review.setOrderId(orderId);
        review.setRating(rating);
        review.setComment(validateContent(comment, imageUrls));
        review.setImageUrls(imageUrls);

        Review saved = reviewRepository.save(review);
        recalculateTargetRating(targetType, targetId);

        return toReviewResponse(saved, caller, null, new ReviewLikeResponse(0, false), List.of());
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
        if (targetType != ReviewTargetType.MAKER && request.photoGuids() != null) {
            review.setImageUrls(resolvePhotos(caller, request.photoGuids()));
        }

        review.setComment(validateContent(request.comment(), review.getImageUrls()));

        Review saved = reviewRepository.save(review);
        recalculateTargetRating(targetType, targetId);

        return toReviewResponse(saved, caller, null, new ReviewLikeResponse(0, false), List.of());
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

    @Override
    @Transactional
    public ReviewReplyResponse reply(
            ReviewTargetType targetType, String targetGuid, String reviewGuid, CreateReviewReplyRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Review review = findCatalogReview(targetType, targetGuid, reviewGuid);
        ReviewReply reply = new ReviewReply();
        reply.setReviewId(review.getId());
        reply.setAuthorUserId(caller.getId());
        reply.setImageUrls(resolvePhotos(caller, request.photoGuids()));
        reply.setComment(validateContent(request.comment(), reply.getImageUrls()));
        return toReplyResponse(reviewReplyRepository.save(reply), caller, null);
    }

    @Override
    @Transactional
    public ReviewLikeResponse setLiked(
            ReviewTargetType targetType, String targetGuid, String reviewGuid, boolean liked) {
        User caller = ServiceContext.getUserOrThrow();
        Review review = findCatalogReview(targetType, targetGuid, reviewGuid);
        String sql = liked
                ? "INSERT INTO review_like (review_id, user_id) VALUES (:reviewId, :userId) ON CONFLICT DO NOTHING"
                : "DELETE FROM review_like WHERE review_id = :reviewId AND user_id = :userId";
        jdbc.sql(sql).param("reviewId", review.getId()).param("userId", caller.getId()).update();
        return readLikes(List.of(review.getId())).getOrDefault(review.getId(), new ReviewLikeResponse(0, false));
    }

    private Map<Long, ReviewLikeResponse> readLikes(List<Long> reviewIds) {
        Long userId = ServiceContext.getUser().map(User::getId).orElse(0L);
        return jdbc.sql("""
                SELECT review_id, COUNT(*) AS like_count, BOOL_OR(user_id = :userId) AS liked
                FROM review_like WHERE review_id IN (:reviewIds) GROUP BY review_id
                """)
                .param("userId", userId).param("reviewIds", reviewIds)
                .query((row, idx) -> Map.entry(row.getLong("review_id"),
                        new ReviewLikeResponse(row.getLong("like_count"), row.getBoolean("liked"))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Review findCatalogReview(ReviewTargetType targetType, String targetGuid, String reviewGuid) {
        Long targetId = resolveTargetId(targetType, targetGuid);
        return reviewRepository.findOneByGuidAndStatus(reviewGuid, StatusCd.ACTIVE)
                .filter(review -> targetType != ReviewTargetType.MAKER && review.getTargetType() == targetType
                        && Objects.equals(review.getTargetId(), targetId))
                .orElseThrow(() -> new NotFoundException(MessageCd.REVIEW_NOT_FOUND));
    }

    private String validateContent(String comment, List<String> imageUrls) {
        String text = comment == null ? "" : comment.trim();
        if (text.isEmpty() && imageUrls.isEmpty()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        return text;
    }

    private List<String> resolvePhotos(User caller, List<String> photoGuids) {
        if (photoGuids == null) return List.of();
        return photoGuids.stream().distinct().map(guid -> fileAssetRepository.findByGuid(guid)
                .filter(asset -> Objects.equals(asset.getUserId(), caller.getId())
                        && asset.getStatus() == StatusCd.ACTIVE && asset.getAssetType() == FileAsset.AssetType.IMAGE
                        && !asset.getName().toLowerCase(Locale.ROOT).endsWith(".svg"))
                .map(objectStorageService::buildPublicObjectUrl)
                .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR))).toList();
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
            User author,
            Map<Long, Company> companiesById,
            ReviewLikeResponse like,
            List<ReviewReplyResponse> replies) {
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
                review.getCreateDt(),
                review.getImageUrls(),
                like.likeCount(),
                like.liked(),
                replies
        );
    }

    private ReviewReplyResponse toReplyResponse(ReviewReply reply, User author, Map<Long, Company> companiesById) {
        return new ReviewReplyResponse(reply.getGuid(), author != null ? author.getGuid() : null,
                author != null ? author.getFullName() : "Usuário", resolveAuthorAvatar(author, companiesById),
                reply.getComment(), reply.getCreateDt(), reply.getImageUrls());
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
