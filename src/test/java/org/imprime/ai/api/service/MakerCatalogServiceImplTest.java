package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.maker.MakerCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerCatalogItemResponse;
import org.imprime.ai.api.http.response.maker.MakerProfileResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerPortfolioItem;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.cache.MakerPresenceCache;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerPortfolioItemRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.service.MakerEligibilityService;
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
import java.time.Duration;
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
    private AssetRepository assetRepository;
    @Mock
    private PrinterCatalogRepository printerCatalogRepository;
    @Mock
    private FavoriteServiceImpl favoriteService;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();
    @Spy
    private MakerPresenceCache makerPresenceCache = new MakerPresenceCache(Duration.ofMinutes(4), 1_000L);
    @Spy
    private MakerEligibilityService makerEligibilityService = new MakerEligibilityService(new JsonCodec());

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
        makerPresenceCache.markOnline(1L);
        makerPresenceCache.markOnline(2L);

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

    @Test
    void listMakersAndProfileReflectRealtimePresenceCacheAndVacationMode() {
        Company activeOnline = new Company();
        activeOnline.setId(10L);
        activeOnline.setGuid("maker-online");
        activeOnline.setSlug("studio-online");
        activeOnline.setName("Studio Online");
        activeOnline.setOnline(false); // stale DB column intentionally false
        activeOnline.setVacationMode(false);
        activeOnline.setStatus(StatusCd.ACTIVE);

        Company offlineInCache = new Company();
        offlineInCache.setId(11L);
        offlineInCache.setGuid("maker-offline");
        offlineInCache.setSlug("studio-offline");
        offlineInCache.setName("Studio Offline");
        offlineInCache.setOnline(true); // stale DB column intentionally true
        offlineInCache.setVacationMode(false);
        offlineInCache.setStatus(StatusCd.ACTIVE);

        Company vacationStudio = new Company();
        vacationStudio.setId(12L);
        vacationStudio.setGuid("maker-vacation");
        vacationStudio.setSlug("studio-vacation");
        vacationStudio.setName("Studio Vacation");
        vacationStudio.setVacationMode(true);
        vacationStudio.setStatus(StatusCd.ACTIVE);

        makerPresenceCache.markOnline(10L);
        makerPresenceCache.markOnline(12L);

        when(companyRepository.findAllByStatus(StatusCd.ACTIVE))
                .thenReturn(List.of(activeOnline, offlineInCache, vacationStudio));
        when(addressRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(eq(EntityType.COMPANY), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of());
        when(favoriteService.findFavoritedTargetIds(FavoriteTargetType.MAKER)).thenReturn(Set.of());

        PageResponse<MakerCatalogItemResponse> onlineOnly = service.listMakers(
                new MakerCatalogFilter(null, null, null, null, null, null, true, null, null, null),
                PageRequest.of(0, 10)
        );
        assertEquals(1, onlineOnly.totalElements());
        assertEquals("maker-online", onlineOnly.content().getFirst().guid());
        assertTrue(onlineOnly.content().getFirst().online());

        when(companyRepository.findByGuidAndStatus("studio-online", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(companyRepository.findBySlugAndStatus("studio-online", StatusCd.ACTIVE)).thenReturn(Optional.of(activeOnline));
        when(favoriteService.isFavorited(FavoriteTargetType.MAKER, 10L)).thenReturn(false);

        MakerProfileResponse onlineProfile = service.getMakerProfile("studio-online");
        assertTrue(onlineProfile.online());

        when(companyRepository.findByGuidAndStatus("studio-vacation", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(companyRepository.findBySlugAndStatus("studio-vacation", StatusCd.ACTIVE)).thenReturn(Optional.of(vacationStudio));
        when(favoriteService.isFavorited(FavoriteTargetType.MAKER, 12L)).thenReturn(false);

        MakerProfileResponse vacationProfile = service.getMakerProfile("studio-vacation");
        assertFalse(vacationProfile.online());
    }

    @Test
    void listMakersExposesCoordinatesAndDeduplicatedPortfolioPreviewsUpToLimitRespectingPublicVisibility() {
        Company publicMaker = new Company();
        publicMaker.setId(21L);
        publicMaker.setGuid("maker-21");
        publicMaker.setSlug("studio-publico");
        publicMaker.setName("Studio Público");
        publicMaker.setPublicPortfolioEnabled(true);
        publicMaker.setStatus(StatusCd.ACTIVE);

        Company privateMaker = new Company();
        privateMaker.setId(22L);
        privateMaker.setGuid("maker-22");
        privateMaker.setSlug("studio-privado-lista");
        privateMaker.setName("Studio Privado");
        privateMaker.setPublicPortfolioEnabled(false);
        privateMaker.setStatus(StatusCd.ACTIVE);

        Address addr21 = new Address();
        addr21.setOwnerId(21L);
        addr21.setOwnerType(EntityType.COMPANY);
        addr21.setCity("Curitiba");
        addr21.setState("PR");
        addr21.setLatitude(-25.4284);
        addr21.setLongitude(-49.2733);
        addr21.setDefaultAddress(true);

        MakerPortfolioItem item1 = new MakerPortfolioItem();
        item1.setCompanyId(21L);
        item1.setTitle("Engrenagem");
        item1.setMaterial(MaterialType.PLA);
        item1.setCoverImageUrl("https://cdn.example.com/1.jpg");
        item1.setImageUrlsJson("[\"https://cdn.example.com/1.jpg\",\"https://cdn.example.com/2.jpg\",\"https://cdn.example.com/3.jpg\",\"https://cdn.example.com/4.jpg\"]");

        MakerPortfolioItem item2 = new MakerPortfolioItem();
        item2.setCompanyId(21L);
        item2.setTitle("Suporte");
        item2.setMaterial(MaterialType.PETG);
        item2.setCoverImageUrl("https://cdn.example.com/5.jpg");
        item2.setImageUrlsJson("[\"https://cdn.example.com/6.jpg\",\"https://cdn.example.com/7.jpg\"]");

        when(companyRepository.findAllByStatus(StatusCd.ACTIVE)).thenReturn(List.of(publicMaker, privateMaker));
        when(addressRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(eq(EntityType.COMPANY), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of(addr21));
        when(favoriteService.findFavoritedTargetIds(FavoriteTargetType.MAKER)).thenReturn(Set.of());
        when(portfolioItemRepository.findAllByCompanyIdInAndStatusOrderByCreateDtDesc(eq(List.of(21L)), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of(item1, item2));

        PageResponse<MakerCatalogItemResponse> result = service.listMakers(null, PageRequest.of(0, 10));

        assertEquals(2, result.totalElements());
        MakerCatalogItemResponse publicResp = result.content().stream()
                .filter(r -> "maker-21".equals(r.guid()))
                .findFirst()
                .orElseThrow();
        MakerCatalogItemResponse privateResp = result.content().stream()
                .filter(r -> "maker-22".equals(r.guid()))
                .findFirst()
                .orElseThrow();

        assertEquals(-25.4284, publicResp.latitude());
        assertEquals(-49.2733, publicResp.longitude());
        assertEquals(List.of(
                "https://cdn.example.com/1.jpg",
                "https://cdn.example.com/2.jpg",
                "https://cdn.example.com/3.jpg",
                "https://cdn.example.com/4.jpg",
                "https://cdn.example.com/5.jpg",
                "https://cdn.example.com/6.jpg"
        ), publicResp.portfolioImages());

        assertNull(privateResp.latitude());
        assertNull(privateResp.longitude());
        assertTrue(privateResp.portfolioImages().isEmpty());
    }
}
