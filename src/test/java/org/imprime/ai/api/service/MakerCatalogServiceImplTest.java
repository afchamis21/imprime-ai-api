package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.maker.MakerCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerCatalogItemResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerPortfolioItemRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.service.impl.FavoriteServiceImpl;
import org.imprime.ai.api.service.impl.MakerCatalogServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerCatalogServiceImplTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private MakerPortfolioItemRepository portfolioItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private FavoriteServiceImpl favoriteService;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private MakerCatalogServiceImpl service;

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void listMakersComputesHaversineDistanceAndFiltersByDistanceAndMaterial() {
        Company nearby = new Company();
        nearby.setId(1L);
        nearby.setGuid("maker-1");
        nearby.setSlug("maker-sp");
        nearby.setName("Studio SP");
        nearby.setOnline(true);
        nearby.setVacationMode(false);
        nearby.setMaterialsJson("[\"PLA\",\"PETG\"]");
        nearby.setMinLeadTimeDays(2);
        nearby.setRatingAvg(new BigDecimal("4.90"));
        nearby.setStatus(StatusCd.ACTIVE);

        Company far = new Company();
        far.setId(2L);
        far.setGuid("maker-2");
        far.setSlug("maker-rj");
        far.setName("Studio RJ");
        far.setOnline(true);
        far.setVacationMode(false);
        far.setMaterialsJson("[\"RESIN\"]");
        far.setMinLeadTimeDays(3);
        far.setRatingAvg(new BigDecimal("4.80"));
        far.setStatus(StatusCd.ACTIVE);

        Address spAddress = new Address();
        spAddress.setOwnerId(1L);
        spAddress.setOwnerType(EntityType.COMPANY);
        spAddress.setCity("São Paulo");
        spAddress.setState("SP");
        spAddress.setLatitude(-23.5505);
        spAddress.setLongitude(-46.6333);
        spAddress.setDefaultAddress(true);

        Address rjAddress = new Address();
        rjAddress.setOwnerId(2L);
        rjAddress.setOwnerType(EntityType.COMPANY);
        rjAddress.setCity("Rio de Janeiro");
        rjAddress.setState("RJ");
        rjAddress.setLatitude(-22.9068);
        rjAddress.setLongitude(-43.1729);
        rjAddress.setDefaultAddress(true);

        when(companyRepository.findAllByStatus(StatusCd.ACTIVE)).thenReturn(List.of(nearby, far));
        when(addressRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(eq(EntityType.COMPANY), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of(spAddress, rjAddress));
        when(favoriteService.findFavoritedTargetIds(FavoriteTargetType.MAKER)).thenReturn(Set.of(1L));

        MakerCatalogFilter filter = new MakerCatalogFilter(
                null, null, 25.0, null, null, null, true, List.of(MaterialType.PETG), -23.5614, -46.6559
        );

        PageResponse<MakerCatalogItemResponse> result = service.listMakers(filter, PageRequest.of(0, 10));

        assertEquals(1, result.totalElements());
        MakerCatalogItemResponse item = result.content().getFirst();
        assertEquals("maker-1", item.guid());
        assertTrue(item.favorited());
        assertNotNull(item.distanceKm());
        assertTrue(item.distanceKm().doubleValue() < 5.0);
    }

    @Test
    void listMakerPortfolioReturnsEmptyPageWhenPublicPortfolioDisabled() {
        Company privateStudio = new Company();
        privateStudio.setId(9L);
        privateStudio.setGuid("maker-9");
        privateStudio.setSlug("studio-privado");
        privateStudio.setPublicPortfolioEnabled(false);
        privateStudio.setStatus(StatusCd.ACTIVE);

        when(companyRepository.findByGuidAndStatus("studio-privado", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(companyRepository.findBySlugAndStatus("studio-privado", StatusCd.ACTIVE)).thenReturn(Optional.of(privateStudio));

        PageResponse<PortfolioItemResponse> page = service.listMakerPortfolio("studio-privado", PageRequest.of(0, 12));

        assertTrue(page.content().isEmpty());
        assertEquals(0L, page.totalElements());
        verifyNoInteractions(portfolioItemRepository);
    }
}
