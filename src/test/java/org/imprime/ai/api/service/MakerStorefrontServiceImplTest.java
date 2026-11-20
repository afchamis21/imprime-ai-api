package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.common.DimensionsRequest;
import org.imprime.ai.api.http.request.maker.CreateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProductRequest;
import org.imprime.ai.api.http.response.maker.MakerProductResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerPortfolioItem;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerPortfolioItemRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.service.impl.MakerStorefrontServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerStorefrontServiceImplTest {

    @Mock
    private MakerPortfolioItemRepository portfolioItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private MakerStorefrontServiceImpl service;

    private User makerUser;
    private Company company;

    @BeforeEach
    void setUp() {
        makerUser = new User();
        makerUser.setId(1L);
        makerUser.setGuid("maker-user-1");
        makerUser.setCompanyId(10L);

        company = new Company();
        company.setId(10L);
        company.setGuid("company-guid-10");
        company.setStatus(StatusCd.ACTIVE);

        ServiceContext.getContext().setUser(makerUser);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void createProductGeneratesUniqueSlugAndDefaultsCoverImage() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(productRepository.existsBySlug("vaso-geometrico")).thenReturn(true);
        when(productRepository.existsBySlug("vaso-geometrico-2")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            saved.setGuid("prod-guid-1");
            return saved;
        });

        CreateMakerProductRequest request = new CreateMakerProductRequest(
                "Vaso Geométrico",
                new BigDecimal("49.90"),
                new BigDecimal("69.90"),
                "decoration",
                List.of("https://cdn.imprime.ai/v1.png", "https://cdn.imprime.ai/v2.png"),
                null,
                "Vaso impresso em PLA",
                MaterialType.PLA,
                new DimensionsRequest(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("150")),
                120,
                List.of("Resistente"),
                true,
                3,
                List.of("Preto", "Branco")
        );

        MakerProductResponse response = service.createProduct(request);

        assertEquals("vaso-geometrico-2", response.slug());
        assertEquals("https://cdn.imprime.ai/v1.png", response.coverImageUrl());
        assertEquals(2, response.customizableColors().size());
        assertTrue(response.inStock());
    }

    @Test
    void updateProductPreservesImmutableSlugAndClearsOldPriceOnZero() {
        Product existing = new Product();
        existing.setId(99L);
        existing.setGuid("prod-guid-99");
        existing.setCompanyId(10L);
        existing.setSlug("slug-original");
        existing.setName("Nome Original");
        existing.setPrice(new BigDecimal("50.00"));
        existing.setOldPrice(new BigDecimal("70.00"));
        existing.setCategory("utilities");
        existing.setMaterial(MaterialType.PETG);
        existing.setStatus(StatusCd.ACTIVE);

        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(productRepository.findByGuidAndStatus("prod-guid-99", StatusCd.ACTIVE)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMakerProductRequest request = new UpdateMakerProductRequest(
                "Novo Nome Completamente Diferente",
                new BigDecimal("39.90"),
                BigDecimal.ZERO,
                null, null, null, null, null, null, null, null, null, null, null
        );

        MakerProductResponse updated = service.updateProduct("prod-guid-99", request);

        assertEquals("slug-original", updated.slug());
        assertEquals("Novo Nome Completamente Diferente", updated.name());
        assertEquals(new BigDecimal("39.90"), updated.price());
        assertNull(updated.oldPrice());
    }

    @Test
    void updateProductRejectsDifferentCompanyOwner() {
        Product foreignProduct = new Product();
        foreignProduct.setId(88L);
        foreignProduct.setGuid("prod-guid-88");
        foreignProduct.setCompanyId(999L);
        foreignProduct.setStatus(StatusCd.ACTIVE);

        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(productRepository.findByGuidAndStatus("prod-guid-88", StatusCd.ACTIVE)).thenReturn(Optional.of(foreignProduct));

        assertThrows(ForbiddenException.class, () -> service.deleteProduct("prod-guid-88"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void deletePortfolioItemMarksInactiveForOwner() {
        MakerPortfolioItem item = new MakerPortfolioItem();
        item.setId(15L);
        item.setGuid("port-15");
        item.setCompanyId(10L);
        item.setStatus(StatusCd.ACTIVE);

        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(portfolioItemRepository.findByGuidAndStatus("port-15", StatusCd.ACTIVE)).thenReturn(Optional.of(item));

        service.deletePortfolioItem("port-15");

        assertEquals(StatusCd.INACTIVE, item.getStatus());
        verify(portfolioItemRepository).save(item);
    }
}
