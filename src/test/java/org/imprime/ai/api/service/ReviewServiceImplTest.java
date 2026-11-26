package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.common.CreateMakerReviewRequest;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.common.CreateReviewReplyRequest;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.Review;
import org.imprime.ai.api.model.ReviewReply;
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
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.ReviewRepository;
import org.imprime.ai.api.repo.db.ReviewReplyRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusService orderStatusService;

    @Mock
    private ReviewReplyRepository reviewReplyRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private ReviewServiceImpl service;

    private User author;
    private Product product;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(7L);
        author.setGuid("user-guid-7");
        author.setFirstName("Lucas");
        author.setLastName("Amorim");
        author.setAvatarUrl("https://cdn.example/buyer-avatar.png");

        product = new Product();
        product.setId(70L);
        product.setGuid("product-guid-70");
        product.setStatus(StatusCd.ACTIVE);

        ServiceContext.getContext().setUser(author);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void createProductReviewRecalculatesTargetAverageAndCount() {
        Review previousReview = new Review();
        previousReview.setRating(4);
        previousReview.setStatus(StatusCd.ACTIVE);

        when(productRepository.findByGuidAndStatus("product-guid-70", StatusCd.ACTIVE)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByAuthorUserIdAndTargetTypeAndTargetIdAndStatus(
                7L, ReviewTargetType.PRODUCT, 70L, StatusCd.ACTIVE)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setGuid("review-guid-new");
            return r;
        });
        when(reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType.PRODUCT, 70L, StatusCd.ACTIVE))
                .thenReturn(List.of(previousReview, reviewWithRating(5)));
        when(productRepository.findById(70L)).thenReturn(Optional.of(product));

        ReviewResponse response = service.createReview(
                ReviewTargetType.PRODUCT,
                "product-guid-70",
                new CreateReviewRequest(5, "Excelente acabamento")
        );

        assertEquals("review-guid-new", response.guid());
        assertEquals("https://cdn.example/buyer-avatar.png", response.authorAvatarUrl());
        assertEquals(5, response.rating());
        assertEquals(new BigDecimal("4.50"), product.getRatingAvg());
        assertEquals(2, product.getReviewsCount());
        verify(productRepository).save(product);
    }

    @Test
    void createReviewThrowsConflictWhenUserAlreadyReviewedTarget() {
        when(productRepository.findByGuidAndStatus("product-guid-70", StatusCd.ACTIVE)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByAuthorUserIdAndTargetTypeAndTargetIdAndStatus(
                7L, ReviewTargetType.PRODUCT, 70L, StatusCd.ACTIVE)).thenReturn(true);

        assertThrows(ConflictException.class, () ->
                service.createReview(ReviewTargetType.PRODUCT, "product-guid-70", new CreateReviewRequest(5, "Duplicado"))
        );
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void deleteReviewRejectsNonAuthorAndResetsRatingOnOwnDelete() {
        Review review = new Review();
        review.setId(500L);
        review.setGuid("rev-500");
        review.setAuthorUserId(99L);
        review.setTargetType(ReviewTargetType.PRODUCT);
        review.setTargetId(70L);
        review.setStatus(StatusCd.ACTIVE);

        when(productRepository.findByGuidAndStatus("product-guid-70", StatusCd.ACTIVE)).thenReturn(Optional.of(product));
        when(reviewRepository.findByGuidAndStatus("rev-500", StatusCd.ACTIVE)).thenReturn(Optional.of(review));

        assertThrows(ForbiddenException.class, () ->
                service.deleteReview(ReviewTargetType.PRODUCT, "product-guid-70", "rev-500")
        );

        review.setAuthorUserId(7L);
        when(reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType.PRODUCT, 70L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(productRepository.findById(70L)).thenReturn(Optional.of(product));

        service.deleteReview(ReviewTargetType.PRODUCT, "product-guid-70", "rev-500");

        assertEquals(StatusCd.INACTIVE, review.getStatus());
        assertEquals(new BigDecimal("0.00"), product.getRatingAvg());
        assertEquals(0, product.getReviewsCount());
    }

    @Test
    void updateReviewUpdatesRatingAndCommentAndRecalculatesTargetAverage() {
        Review review = new Review();
        review.setId(501L);
        review.setGuid("rev-501");
        review.setAuthorUserId(7L);
        review.setTargetType(ReviewTargetType.PRODUCT);
        review.setTargetId(70L);
        review.setRating(3);
        review.setComment("Razoável");
        review.setStatus(StatusCd.ACTIVE);

        when(productRepository.findByGuidAndStatus("product-guid-70", StatusCd.ACTIVE)).thenReturn(Optional.of(product));
        when(reviewRepository.findByGuidAndStatus("rev-501", StatusCd.ACTIVE)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType.PRODUCT, 70L, StatusCd.ACTIVE))
                .thenReturn(List.of(review));
        when(productRepository.findById(70L)).thenReturn(Optional.of(product));

        ReviewResponse updated = service.updateReview(
                ReviewTargetType.PRODUCT,
                "product-guid-70",
                "rev-501",
                new CreateReviewRequest(5, "Editado: ficou ótimo!")
        );

        assertEquals("rev-501", updated.guid());
        assertEquals(5, updated.rating());
        assertEquals("Editado: ficou ótimo!", updated.comment());
        assertEquals(new BigDecimal("5.00"), product.getRatingAvg());
        assertEquals(1, product.getReviewsCount());
    }

    @Test
    void replyAcceptsAnOwnedPhotoAndLeavesTheRatingUntouched() {
        Review review = new Review();
        review.setId(501L);
        review.setTargetType(ReviewTargetType.PRODUCT);
        review.setTargetId(70L);
        FileAsset photo = new FileAsset();
        photo.setUserId(author.getId());
        photo.setStatus(StatusCd.ACTIVE);
        photo.setAssetType(FileAsset.AssetType.IMAGE);
        photo.setName("print.png");
        String url = "https://cdn.example/print.png";

        when(productRepository.findByGuidAndStatus(product.getGuid(), StatusCd.ACTIVE)).thenReturn(Optional.of(product));
        when(reviewRepository.findOneByGuidAndStatus("rev-501", StatusCd.ACTIVE)).thenReturn(Optional.of(review));
        when(fileAssetRepository.findByGuid("photo-guid")).thenReturn(Optional.of(photo));
        when(objectStorageService.buildPublicObjectUrl(photo)).thenReturn(url);
        when(reviewReplyRepository.save(any(ReviewReply.class))).thenAnswer(invocation -> {
            ReviewReply reply = invocation.getArgument(0);
            assertEquals(501L, reply.getReviewId());
            assertEquals(author.getId(), reply.getAuthorUserId());
            return reply;
        });

        var response = service.reply(ReviewTargetType.PRODUCT, product.getGuid(), "rev-501",
                new CreateReviewReplyRequest("", List.of("photo-guid")));

        assertEquals(List.of(url), response.imageUrls());
        assertEquals("", response.comment());
        verify(productRepository, never()).save(any());
    }

    @Test
    void replyCannotBeAddedThroughAPathForAnotherProduct() {
        Review review = new Review();
        review.setTargetType(ReviewTargetType.PRODUCT);
        review.setTargetId(71L);
        when(productRepository.findByGuidAndStatus(product.getGuid(), StatusCd.ACTIVE)).thenReturn(Optional.of(product));
        when(reviewRepository.findOneByGuidAndStatus("rev-501", StatusCd.ACTIVE)).thenReturn(Optional.of(review));

        assertThrows(NotFoundException.class, () -> service.reply(ReviewTargetType.PRODUCT,
                product.getGuid(), "rev-501", new CreateReviewReplyRequest("Resposta", List.of())));
        verifyNoInteractions(reviewReplyRepository);
    }

    @Test
    void reviewRejectsAPhotoUploadedByAnotherUser() {
        FileAsset photo = new FileAsset();
        photo.setUserId(99L);
        when(productRepository.findByGuidAndStatus(product.getGuid(), StatusCd.ACTIVE)).thenReturn(Optional.of(product));
        when(fileAssetRepository.findByGuid("other-photo-guid")).thenReturn(Optional.of(photo));

        assertThrows(BadRequestException.class, () -> service.createReview(ReviewTargetType.PRODUCT,
                product.getGuid(), new CreateReviewRequest(5, "", List.of("other-photo-guid"))));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createOrderMakerReviewSucceedsForCompletedOrderAndUpdatesCompanyRating() {
        Company maker = activeCompany(20L, "comp-20");
        Order order = activeOrder(900L, "order-900", 7L, 20L);
        OrderStatus completed = statusOf(OrderStatusCd.COMPLETED);

        when(orderRepository.findOrderByGuid("order-900")).thenReturn(Optional.of(order));
        when(companyRepository.findById(20L)).thenReturn(Optional.of(maker));
        when(orderStatusService.findLatest(order)).thenReturn(completed);
        when(reviewRepository.existsByAuthorUserIdAndTargetTypeAndOrderIdAndStatus(
                7L, ReviewTargetType.MAKER, 900L, StatusCd.ACTIVE)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setGuid("rev-maker-1");
            return r;
        });
        when(reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType.MAKER, 20L, StatusCd.ACTIVE))
                .thenReturn(List.of(reviewWithRating(4), reviewWithRating(5)));

        ReviewResponse response = service.createOrderMakerReview(
                "order-900",
                new CreateReviewRequest(5, "Maker super atencioso!")
        );

        assertEquals("rev-maker-1", response.guid());
        assertEquals(5, response.rating());
        assertEquals(new BigDecimal("4.50"), maker.getRatingAvg());
        assertEquals(2, maker.getReviewsCount());
        verify(companyRepository).save(maker);
    }

    @Test
    void createOrderMakerReviewRejectsOrderNotCompleted() {
        Company maker = activeCompany(20L, "comp-20");
        Order order = activeOrder(900L, "order-900", 7L, 20L);

        when(orderRepository.findOrderByGuid("order-900")).thenReturn(Optional.of(order));
        when(companyRepository.findById(20L)).thenReturn(Optional.of(maker));
        when(orderStatusService.findLatest(order)).thenReturn(statusOf(OrderStatusCd.IN_TRANSIT));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.createOrderMakerReview("order-900", new CreateReviewRequest(5, "Ainda em trânsito"))
        );

        assertEquals(MessageCd.REVIEW_ORDER_NOT_ELIGIBLE, ex.getMessageCd());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createOrderMakerReviewRejectsNonBuyer() {
        Order order = activeOrder(900L, "order-900", 999L, 20L);
        when(orderRepository.findOrderByGuid("order-900")).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () ->
                service.createOrderMakerReview("order-900", new CreateReviewRequest(5, "Não comprei"))
        );
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createOrderMakerReviewRejectsDuplicateReviewForSameOrder() {
        Company maker = activeCompany(20L, "comp-20");
        Order order = activeOrder(900L, "order-900", 7L, 20L);

        when(orderRepository.findOrderByGuid("order-900")).thenReturn(Optional.of(order));
        when(companyRepository.findById(20L)).thenReturn(Optional.of(maker));
        when(orderStatusService.findLatest(order)).thenReturn(statusOf(OrderStatusCd.COMPLETED));
        when(reviewRepository.existsByAuthorUserIdAndTargetTypeAndOrderIdAndStatus(
                7L, ReviewTargetType.MAKER, 900L, StatusCd.ACTIVE)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.createOrderMakerReview("order-900", new CreateReviewRequest(5, "Repetido"))
        );

        assertEquals(MessageCd.REVIEW_ALREADY_EXISTS, ex.getMessageCd());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createMakerReviewLinksLatestUnreviewedCompletedOrder() {
        Company maker = activeCompany(20L, "studio-top");
        Order newestCompleted = activeOrder(902L, "order-902", 7L, 20L);
        Order olderReviewed = activeOrder(901L, "order-901", 7L, 20L);

        Review existingFor901 = new Review();
        existingFor901.setOrderId(901L);
        existingFor901.setStatus(StatusCd.ACTIVE);

        when(companyRepository.findByGuidAndStatus("studio-top", StatusCd.ACTIVE)).thenReturn(Optional.of(maker));
        when(orderRepository.findAllByBuyerIdAndVendorIdAndStatusOrderByCreateDtDesc(7L, 20L, StatusCd.ACTIVE))
                .thenReturn(List.of(newestCompleted, olderReviewed));
        when(orderStatusService.findLatest(newestCompleted)).thenReturn(statusOf(OrderStatusCd.COMPLETED));
        when(orderStatusService.findLatest(olderReviewed)).thenReturn(statusOf(OrderStatusCd.COMPLETED));
        when(reviewRepository.findAllByAuthorUserIdAndTargetTypeAndTargetIdAndStatus(
                7L, ReviewTargetType.MAKER, 20L, StatusCd.ACTIVE)).thenReturn(List.of(existingFor901));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setGuid("rev-maker-902");
            return r;
        });
        when(reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType.MAKER, 20L, StatusCd.ACTIVE))
                .thenReturn(List.of(reviewWithRating(5), reviewWithRating(5)));
        when(companyRepository.findById(20L)).thenReturn(Optional.of(maker));

        ReviewResponse response = service.createMakerReview(
                "studio-top",
                new CreateMakerReviewRequest(5, "Segunda compra impecável", null)
        );

        assertEquals("rev-maker-902", response.guid());
        assertEquals(new BigDecimal("5.00"), maker.getRatingAvg());
        assertEquals(2, maker.getReviewsCount());
    }

    @Test
    void createMakerReviewRejectsWithoutCompletedOrder() {
        Company maker = activeCompany(20L, "studio-top");
        Order inProd = activeOrder(901L, "order-901", 7L, 20L);

        when(companyRepository.findByGuidAndStatus("studio-top", StatusCd.ACTIVE)).thenReturn(Optional.of(maker));
        when(orderRepository.findAllByBuyerIdAndVendorIdAndStatusOrderByCreateDtDesc(7L, 20L, StatusCd.ACTIVE))
                .thenReturn(List.of(inProd));
        when(orderStatusService.findLatest(inProd)).thenReturn(statusOf(OrderStatusCd.IN_PRODUCTION));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.createMakerReview("studio-top", new CreateMakerReviewRequest(5, "Sem entrega ainda", null))
        );

        assertEquals(MessageCd.REVIEW_ORDER_NOT_ELIGIBLE, ex.getMessageCd());
        verify(reviewRepository, never()).save(any());
    }

    private Company activeCompany(Long id, String guid) {
        Company company = new Company();
        company.setId(id);
        company.setGuid(guid);
        company.setStatus(StatusCd.ACTIVE);
        return company;
    }

    private Order activeOrder(Long id, String guid, Long buyerId, Long vendorId) {
        Order order = new Order();
        order.setId(id);
        order.setGuid(guid);
        order.setBuyerId(buyerId);
        order.setVendorId(vendorId);
        order.setStatus(StatusCd.ACTIVE);
        return order;
    }

    private OrderStatus statusOf(OrderStatusCd statusCd) {
        OrderStatus status = new OrderStatus();
        status.setStatusCd(statusCd);
        return status;
    }

    private Review reviewWithRating(int stars) {
        Review review = new Review();
        review.setRating(stars);
        review.setStatus(StatusCd.ACTIVE);
        return review;
    }
}
