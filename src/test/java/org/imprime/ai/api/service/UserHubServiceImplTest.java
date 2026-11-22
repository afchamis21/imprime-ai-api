package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.common.CreateReviewRequest;
import org.imprime.ai.api.http.request.user.UpdateUserAvatarRequest;
import org.imprime.ai.api.http.request.user.UpdateUserProfileRequest;
import org.imprime.ai.api.http.response.user.ModelDownloadResponse;
import org.imprime.ai.api.http.response.user.UserReviewResponse;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.PurchasedModel;
import org.imprime.ai.api.model.Review;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.FullUserDTO;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.ReviewTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.cache.UserInMemoryCache;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.PurchasedModelRepository;
import org.imprime.ai.api.repo.db.ReviewRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.UserHubServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserHubServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserInMemoryCache userInMemoryCache;
    @Mock
    private UserService userService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Mock
    private PurchasedModelRepository purchasedModelRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private UserHubServiceImpl service;

    private User buyer;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(4L);
        buyer.setGuid("buyer-4");
        ServiceContext.getContext().setUser(buyer);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void generatePurchasedModelDownloadUrlIncrementsCountersAndReturnsSignedUrl() {
        PurchasedModel purchase = new PurchasedModel();
        purchase.setId(300L);
        purchase.setGuid("pur-300");
        purchase.setBuyerUserId(4L);
        purchase.setModel3dId(80L);
        purchase.setLicense(LicenseType.PERSONAL);
        purchase.setPricePaid(new BigDecimal("34.90"));
        purchase.setDownloadsCount(2);
        purchase.setStatus(StatusCd.ACTIVE);

        Model3D model = new Model3D();
        model.setId(80L);
        model.setGuid("mdl-80");
        model.setFileAssetId(500L);
        model.setDownloadsCount(100L);

        FileAsset asset = new FileAsset();
        asset.setId(500L);
        asset.setAssetUrl("models/author/dragon.stl");

        Instant expiresAt = Instant.now().plusSeconds(900);
        when(purchasedModelRepository.findByGuidAndStatus("pur-300", StatusCd.ACTIVE)).thenReturn(Optional.of(purchase));
        when(model3DRepository.findById(80L)).thenReturn(Optional.of(model));
        when(fileAssetRepository.findById(500L)).thenReturn(Optional.of(asset));
        when(objectStorageService.createPresignedDownloadUrl(eq(asset), any(Duration.class)))
                .thenReturn(new ObjectStorageService.PresignedDownloadUrl("https://objectstorage.test/p/par-token", expiresAt));

        ModelDownloadResponse response = service.generatePurchasedModelDownloadUrl("pur-300");

        assertEquals("pur-300", response.purchaseGuid());
        assertEquals("mdl-80", response.modelGuid());
        assertEquals("https://objectstorage.test/p/par-token", response.downloadUrl());
        assertEquals(3, response.downloadsCount());
        assertEquals(101L, model.getDownloadsCount());
        assertNotNull(purchase.getLastDownloadedAt());
        verify(purchasedModelRepository).save(purchase);
        verify(model3DRepository).save(model);
    }

    @Test
    void generatePurchasedModelDownloadUrlRejectsNonOwner() {
        PurchasedModel foreignPurchase = new PurchasedModel();
        foreignPurchase.setGuid("pur-999");
        foreignPurchase.setBuyerUserId(99L);
        foreignPurchase.setStatus(StatusCd.ACTIVE);

        when(purchasedModelRepository.findByGuidAndStatus("pur-999", StatusCd.ACTIVE))
                .thenReturn(Optional.of(foreignPurchase));

        assertThrows(ForbiddenException.class, () -> service.generatePurchasedModelDownloadUrl("pur-999"));
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void updateAndDeleteUserReviewRecalculatesTargetRating() {
        Product product = new Product();
        product.setId(55L);
        product.setGuid("prod-55");
        product.setName("Suporte Controle");

        Review review = new Review();
        review.setId(900L);
        review.setGuid("rev-900");
        review.setAuthorUserId(4L);
        review.setTargetType(ReviewTargetType.PRODUCT);
        review.setTargetId(55L);
        review.setRating(3);
        review.setComment("Bom");
        review.setStatus(StatusCd.ACTIVE);

        when(reviewRepository.findByGuidAndStatus("rev-900", StatusCd.ACTIVE)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType.PRODUCT, 55L, StatusCd.ACTIVE))
                .thenReturn(List.of(review));
        when(productRepository.findById(55L)).thenReturn(Optional.of(product));

        UserReviewResponse updated = service.updateUserReview("rev-900", new CreateReviewRequest(5, "Excelente!"));
        assertEquals("rev-900", updated.reviewGuid());
        assertEquals(5, updated.rating());
        assertEquals("Excelente!", updated.comment());
        assertEquals(new BigDecimal("5.00"), product.getRatingAvg());

        when(reviewRepository.findAllByTargetTypeAndTargetIdAndStatus(ReviewTargetType.PRODUCT, 55L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        service.deleteUserReview("rev-900");
        assertEquals(StatusCd.INACTIVE, review.getStatus());
        assertEquals(new BigDecimal("0.00"), product.getRatingAvg());
        assertEquals(0, product.getReviewsCount());
    }

    @Test
    void updateAvatarPersistsAvatarUrlAndRefreshesCache() {
        buyer.setFirstName("Lucas");
        buyer.setLastName("Amorim");
        buyer.setEmail("lucas@imprime.ai");
        buyer.setPhoneNumber("11987654321");

        when(userRepository.findById(4L)).thenReturn(Optional.of(buyer));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getFullUserDetails(any(User.class)))
                .thenAnswer(inv -> FullUserDTO.from(inv.getArgument(0), List.of()));

        FullUserDTO updated = service.updateAvatar(
                new UpdateUserAvatarRequest("  https://cdn.example/avatar.png  ")
        );

        assertEquals("https://cdn.example/avatar.png", buyer.getAvatarUrl());
        assertEquals("https://cdn.example/avatar.png", updated.avatarUrl());
        assertEquals(
                "https://cdn.example/avatar.png",
                ServiceContext.getContext().getUser().map(User::getAvatarUrl).orElse(null)
        );
        verify(userRepository).save(buyer);
        verify(userInMemoryCache).put(buyer);

        FullUserDTO cleared = service.updateAvatar(new UpdateUserAvatarRequest("   "));
        assertNull(buyer.getAvatarUrl());
        assertNull(cleared.avatarUrl());
    }

    @Test
    void updateProfilePreservesAvatarWhenNullAndUpdatesWhenProvided() {
        buyer.setFirstName("Lucas");
        buyer.setLastName("Amorim");
        buyer.setEmail("lucas@imprime.ai");
        buyer.setPhoneNumber("11987654321");
        buyer.setAvatarUrl("https://cdn.example/original.png");

        when(userRepository.findById(4L)).thenReturn(Optional.of(buyer));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getFullUserDetails(any(User.class)))
                .thenAnswer(inv -> FullUserDTO.from(inv.getArgument(0), List.of()));

        FullUserDTO preserved = service.updateProfile(new UpdateUserProfileRequest(
                "Lucas", "Amorim", "lucas@imprime.ai", "11987654321", null
        ));
        assertEquals("https://cdn.example/original.png", preserved.avatarUrl());

        FullUserDTO replaced = service.updateProfile(new UpdateUserProfileRequest(
                "Lucas", "Amorim", "lucas@imprime.ai", "11987654321", "  https://cdn.example/updated.png  "
        ));
        assertEquals("https://cdn.example/updated.png", buyer.getAvatarUrl());
        assertEquals("https://cdn.example/updated.png", replaced.avatarUrl());
    }
}
