package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.response.common.ReviewResponse;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.Review;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.ReviewRepository;
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

    private Review reviewWithRating(int stars) {
        Review review = new Review();
        review.setRating(stars);
        review.setStatus(StatusCd.ACTIVE);
        return review;
    }
}
