package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.user.UpdateUserProfileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.user.ModelDownloadResponse;
import org.imprime.ai.api.http.response.user.PurchasedModelResponse;
import org.imprime.ai.api.http.response.user.UserReviewResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.PurchasedModel;
import org.imprime.ai.api.model.Review;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.FullUserDTO;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.repo.cache.UserInMemoryCache;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.PurchasedModelRepository;
import org.imprime.ai.api.repo.db.ReviewRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.ObjectStorageService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.service.UserHubService;
import org.imprime.ai.api.service.UserService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.PageUtils;
import org.imprime.ai.api.util.PageableSortMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserHubServiceImpl implements UserHubService {
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);
    private static final Map<String, String> REVIEW_SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "rating", "rating"
    );
    private static final Sort REVIEW_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createDt");

    private final UserRepository userRepository;
    private final UserInMemoryCache userInMemoryCache;
    private final UserService userService;
    private final OrderRepository orderRepository;
    private final OrderViewAssembler orderViewAssembler;
    private final PurchasedModelRepository purchasedModelRepository;
    private final Model3DRepository model3DRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ReviewRepository reviewRepository;
    private final ObjectStorageService objectStorageService;

    @Override
    @Transactional
    public FullUserDTO updateProfile(UpdateUserProfileRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        User user = userRepository.findById(caller.getId()).orElseThrow(UnauthorizedException::new);

        if (!request.email().equalsIgnoreCase(user.getEmail()) && userRepository.existsUserByEmail(request.email())) {
            throw new BadRequestException(MessageCd.USER_EMAIL_ALREADY_REGISTERED);
        }

        if (!request.phoneNumber().equals(user.getPhoneNumber()) && userRepository.existsUserByPhoneNumber(request.phoneNumber())) {
            throw new BadRequestException(MessageCd.USER_PHONE_ALREADY_REGISTERED);
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());

        User saved = userRepository.save(user);
        userInMemoryCache.put(saved);
        ServiceContext.getContext().setUser(saved);

        return userService.getFullUserDetails(saved);
    }

    @Override
    public PageResponse<OrderSummaryResponse> listUserOrders(OrderStatusCd status, String query, Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        List<Order> orders = orderRepository.findAllByBuyerIdAndStatusOrderByCreateDtDesc(caller.getId(), StatusCd.ACTIVE);
        return orderViewAssembler.filterAndPaginate(orders, status, query, null, null, pageable);
    }

    @Override
    public PageResponse<PurchasedModelResponse> listPurchasedModels(
            String query,
            FileFormat3D format,
            Boolean customizableOnly,
            Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        List<PurchasedModel> purchases = purchasedModelRepository
                .findAllByBuyerUserIdAndStatusOrderByPurchasedAtDesc(caller.getId(), StatusCd.ACTIVE);

        if (purchases.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> modelIds = purchases.stream()
                .map(PurchasedModel::getModel3dId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Model3D> modelsById = modelIds.isEmpty()
                ? Map.of()
                : model3DRepository.findAllByIdIn(modelIds).stream()
                .collect(Collectors.toMap(Model3D::getId, m -> m, (a, b) -> a));

        Set<Long> authorIds = modelsById.values().stream()
                .map(Model3D::getAuthorUserId)
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

        String normalizedQuery = query != null && !query.isBlank() ? query.trim().toLowerCase(Locale.ROOT) : null;

        List<PurchasedModelResponse> filtered = purchases.stream()
                .map(purchase -> toPurchasedModelResponse(purchase, modelsById.get(purchase.getModel3dId()), authorsById, companiesById))
                .filter(Objects::nonNull)
                .filter(item -> format == null || format.equals(item.format()))
                .filter(item -> !Boolean.TRUE.equals(customizableOnly) || item.customizable())
                .filter(item -> normalizedQuery == null
                        || containsIgnoreCase(item.title(), normalizedQuery)
                        || containsIgnoreCase(item.designerName(), normalizedQuery))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    @Transactional
    public ModelDownloadResponse generatePurchasedModelDownloadUrl(String purchaseGuid) {
        User caller = ServiceContext.getUserOrThrow();
        PurchasedModel purchase = purchasedModelRepository.findByGuidAndStatus(purchaseGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PURCHASED_MODEL_NOT_FOUND));

        if (!caller.getId().equals(purchase.getBuyerUserId())) {
            throw new ForbiddenException();
        }

        Model3D model = model3DRepository.findById(purchase.getModel3dId())
                .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));

        if (model.getFileAssetId() == null) {
            throw new NotFoundException(MessageCd.MODEL_NOT_FOUND);
        }

        FileAsset asset = fileAssetRepository.findById(model.getFileAssetId())
                .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));

        ObjectStorageService.PresignedDownloadUrl presigned =
                objectStorageService.createPresignedDownloadUrl(asset, DOWNLOAD_URL_TTL);

        int updatedDownloads = (purchase.getDownloadsCount() != null ? purchase.getDownloadsCount() : 0) + 1;
        purchase.setDownloadsCount(updatedDownloads);
        purchase.setLastDownloadedAt(Instant.now());
        purchasedModelRepository.save(purchase);

        long modelDownloads = (model.getDownloadsCount() != null ? model.getDownloadsCount() : 0L) + 1L;
        model.setDownloadsCount(modelDownloads);
        model3DRepository.save(model);

        return new ModelDownloadResponse(
                purchase.getGuid(),
                model.getGuid(),
                presigned.url(),
                presigned.expiresAt(),
                updatedDownloads
        );
    }

    @Override
    public PageResponse<UserReviewResponse> listUserReviews(ReviewTargetType targetType, Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        Pageable mapped = PageableSortMapper.remap(pageable, REVIEW_SORT_MAPPING, REVIEW_DEFAULT_SORT);

        Page<Review> page = targetType == null
                ? reviewRepository.findAllByAuthorUserIdAndStatus(caller.getId(), StatusCd.ACTIVE, mapped)
                : reviewRepository.findAllByAuthorUserIdAndTargetTypeAndStatus(caller.getId(), targetType, StatusCd.ACTIVE, mapped);

        Map<ReviewTargetType, Set<Long>> targetIdsByType = new EnumMap<>(ReviewTargetType.class);
        Set<Long> orderIds = new HashSet<>();

        for (Review review : page.getContent()) {
            targetIdsByType.computeIfAbsent(review.getTargetType(), ignored -> new HashSet<>()).add(review.getTargetId());
            if (review.getOrderId() != null) {
                orderIds.add(review.getOrderId());
            }
        }

        Set<Long> productIds = targetIdsByType.getOrDefault(ReviewTargetType.PRODUCT, Set.of());
        Map<Long, Product> productsById = productIds.isEmpty()
                ? Map.of()
                : productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Set<Long> modelIds = targetIdsByType.getOrDefault(ReviewTargetType.MODEL_3D, Set.of());
        Map<Long, Model3D> modelsById = modelIds.isEmpty()
                ? Map.of()
                : model3DRepository.findAllByIdIn(modelIds).stream()
                .collect(Collectors.toMap(Model3D::getId, m -> m, (a, b) -> a));

        Set<Long> makerIds = targetIdsByType.getOrDefault(ReviewTargetType.MAKER, Set.of());
        Map<Long, Company> companiesById = makerIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(makerIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        Map<Long, Order> ordersById = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));

        Page<UserReviewResponse> mappedPage = page.map(review -> toUserReviewResponse(
                review, productsById, modelsById, companiesById, ordersById
        ));
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public UserReviewResponse updateUserReview(String reviewGuid, CreateReviewRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Review review = reviewRepository.findByGuidAndStatus(reviewGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.REVIEW_NOT_FOUND));

        if (!Objects.equals(review.getAuthorUserId(), caller.getId())) {
            throw new ForbiddenException();
        }

        review.setRating(request.rating());
        review.setComment(request.comment().trim());

        Review saved = reviewRepository.save(review);
        recalculateTargetRating(saved.getTargetType(), saved.getTargetId());

        Map<Long, Product> productsById = saved.getTargetType() == ReviewTargetType.PRODUCT
                ? productRepository.findById(saved.getTargetId()).map(p -> Map.of(p.getId(), p)).orElse(Map.of())
                : Map.of();
        Map<Long, Model3D> modelsById = saved.getTargetType() == ReviewTargetType.MODEL_3D
                ? model3DRepository.findById(saved.getTargetId()).map(m -> Map.of(m.getId(), m)).orElse(Map.of())
                : Map.of();
        Map<Long, Company> companiesById = saved.getTargetType() == ReviewTargetType.MAKER
                ? companyRepository.findById(saved.getTargetId()).map(c -> Map.of(c.getId(), c)).orElse(Map.of())
                : Map.of();
        Map<Long, Order> ordersById = saved.getOrderId() != null
                ? orderRepository.findById(saved.getOrderId()).map(o -> Map.of(o.getId(), o)).orElse(Map.of())
                : Map.of();

        return toUserReviewResponse(saved, productsById, modelsById, companiesById, ordersById);
    }

    @Override
    @Transactional
    public void deleteUserReview(String reviewGuid) {
        User caller = ServiceContext.getUserOrThrow();
        Review review = reviewRepository.findByGuidAndStatus(reviewGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.REVIEW_NOT_FOUND));

        if (!Objects.equals(review.getAuthorUserId(), caller.getId())) {
            throw new ForbiddenException();
        }

        review.setStatus(StatusCd.INACTIVE);
        reviewRepository.save(review);
        recalculateTargetRating(review.getTargetType(), review.getTargetId());
    }

    private void recalculateTargetRating(ReviewTargetType targetType, Long targetId) {
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

    private PurchasedModelResponse toPurchasedModelResponse(
            PurchasedModel purchase,
            Model3D model,
            Map<Long, User> authorsById,
            Map<Long, Company> companiesById) {
        if (model == null) {
            return null;
        }
        User author = model.getAuthorUserId() != null ? authorsById.get(model.getAuthorUserId()) : null;
        Company authorCompany = (author != null && author.getCompanyId() != null)
                ? companiesById.get(author.getCompanyId())
                : null;

        String designerName = authorCompany != null
                ? CatalogDtoMapper.firstNonBlank(authorCompany.getName(), author.getFullName())
                : (author != null ? author.getFullName() : "Designer");

        return new PurchasedModelResponse(
                purchase.getGuid(),
                model.getGuid(),
                model.getSlug(),
                model.getName(),
                designerName,
                model.getFormat(),
                model.getFileSizeBytes(),
                CatalogDtoMapper.toDimensionsDto(model.getWidthMm(), model.getDepthMm(), model.getHeightMm()),
                purchase.getPurchasedAt(),
                purchase.getPricePaid(),
                Boolean.TRUE.equals(model.getCustomizable()),
                purchase.getLicense(),
                purchase.getDownloadsCount() != null ? purchase.getDownloadsCount() : 0,
                model.getRecommendedMaterial(),
                model.getCoverImageUrl()
        );
    }

    private UserReviewResponse toUserReviewResponse(
            Review review,
            Map<Long, Product> productsById,
            Map<Long, Model3D> modelsById,
            Map<Long, Company> companiesById,
            Map<Long, Order> ordersById) {
        String targetGuid = null;
        String targetName = null;

        switch (review.getTargetType()) {
            case PRODUCT -> {
                Product product = productsById.get(review.getTargetId());
                if (product != null) {
                    targetGuid = product.getGuid();
                    targetName = product.getName();
                }
            }
            case MODEL_3D -> {
                Model3D model = modelsById.get(review.getTargetId());
                if (model != null) {
                    targetGuid = model.getGuid();
                    targetName = model.getName();
                }
            }
            case MAKER -> {
                Company company = companiesById.get(review.getTargetId());
                if (company != null) {
                    targetGuid = company.getGuid();
                    targetName = company.getName();
                }
            }
        }

        String orderGuid = review.getOrderId() != null
                ? ordersById.getOrDefault(review.getOrderId(), new Order()).getGuid()
                : null;

        return new UserReviewResponse(
                review.getGuid(),
                review.getTargetType(),
                targetGuid,
                targetName,
                orderGuid,
                review.getRating(),
                review.getComment(),
                review.getCreateDt()
        );
    }

    private boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }
}
