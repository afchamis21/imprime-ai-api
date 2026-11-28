package org.imprime.ai.api.service;

import org.imprime.ai.api.controller.PrintPricingController;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.InstantPrintQuoteRequest;
import org.imprime.ai.api.http.response.order.InstantPrintQuoteResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerMaterialPrice;
import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.CompanyAndAddress;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.InstantQuoteSortBy;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.SetupFeeScope;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyDAO;
import org.imprime.ai.api.repo.db.MakerMaterialPriceRepository;
import org.imprime.ai.api.repo.db.MakerPricingProfileRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.service.impl.InstantPrintQuoteServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InstantPrintQuoteServiceImplTest {

    @Mock
    private CompanyDAO companyDAO;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private MakerPricingProfileRepository pricingProfileRepository;
    @Mock
    private MakerMaterialPriceRepository materialPriceRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private PrinterCatalogRepository printerCatalogRepository;

    private InstantPrintQuoteServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonCodec jsonCodec = new JsonCodec();
        service = new InstantPrintQuoteServiceImpl(
                companyDAO,
                addressRepository,
                pricingProfileRepository,
                materialPriceRepository,
                assetRepository,
                printerCatalogRepository,
                new MakerEligibilityService(jsonCodec),
                new PrinterSpeedResolver(),
                new MarketplaceQuoteCalculator()
        );
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void quotesNearbyEligibleMakersSortedByPriceAndExcludesIneligible() {
        Company fastMaker = maker(1L, "m-fast", "Fast Bambu", true, new BigDecimal("4.9"));
        Company slowMaker = maker(2L, "m-slow", "Classic Ender", true, new BigDecimal("4.5"));
        Company pausedMaker = maker(3L, "m-paused", "Paused Maker", false, new BigDecimal("5.0"));

        Address ca1 = cityAddr(1L, "São Bernardo", "SP");
        Address ca2 = cityAddr(2L, "Santo André", "SP");
        Address ca3 = cityAddr(3L, "São Caetano", "SP");

        when(companyDAO.findNearestCompaniesByAddress(-23.6, -46.5, 50_000.0, 90))
                .thenReturn(List.of(
                        new CompanyAndAddress(slowMaker, ca2, 3000.0),
                        new CompanyAndAddress(fastMaker, ca1, 7000.0),
                        new CompanyAndAddress(pausedMaker, ca3, 1500.0)
                ));

        MakerPricingProfile p1 = profile(1L, true, "5.00", "15.00");
        MakerPricingProfile p2 = profile(2L, true, "5.00", "15.00");
        MakerPricingProfile p3 = profile(3L, false, "4.00", "10.00");
        when(pricingProfileRepository.findAllByCompanyIdInAndStatus(List.of(2L, 1L, 3L), StatusCd.ACTIVE))
                .thenReturn(List.of(p1, p2, p3));

        when(materialPriceRepository.findAllByCompanyIdInAndStatus(List.of(2L, 1L, 3L), StatusCd.ACTIVE))
                .thenReturn(List.of(
                        matPrice(1L, MaterialType.PETG, "0.2400"),
                        matPrice(2L, MaterialType.PETG, "0.2400"),
                        matPrice(3L, MaterialType.PETG, "0.2000")
                ));

        Asset a1 = printerAsset(1L, "p1", "Bambu Lab A1", PrinterSpeedTier.HIGH_SPEED);
        Asset a2 = printerAsset(2L, "p2", "Ender 3 V2", PrinterSpeedTier.STANDARD);
        when(assetRepository.findAllByCompanyIdInAndStatus(List.of(2L, 1L, 3L), StatusCd.ACTIVE))
                .thenReturn(List.of(a1, a2));

        InstantPrintQuoteRequest req = new InstantPrintQuoteRequest(
                null, -23.6, -46.5, 50.0, 30, InstantQuoteSortBy.PRICE_ASC, "bambu", "Bambu Lab A1 0.4 nozzle",
                List.of(new InstantPrintQuoteRequest.PlateDnaInput(
                        1, "Bracket", "PETG", 1,
                        new BigDecimal("50.00"), new BigDecimal("5.00"), 3600,
                        new InstantPrintQuoteRequest.BoundingBoxMmInput(new BigDecimal("80"), new BigDecimal("60"), new BigDecimal("40")),
                        new BigDecimal("0.20"), 20, "gyroid", 3, true, "buildplate", "brim", "raw", "normal", false, 1
                ))
        );

        InstantPrintQuoteResponse res = service.quoteNearbyMakers(req);

        assertEquals(3, res.evaluatedMakersCount());
        assertEquals(2, res.eligibleMakersCount());
        assertEquals(2, res.quotes().size());
        // Fast maker has 1.0x machine time (1h * 5.00 = 5.00), Slow has 2.2x (2.2h * 5.00 = 11.00) -> Fast is cheaper
        assertEquals("m-fast", res.quotes().getFirst().makerGuid());
        assertEquals(3600, res.quotes().getFirst().estimatedPrintTimeSeconds());
        assertEquals(PrinterSpeedTier.HIGH_SPEED, res.quotes().getFirst().matchedPrinter().speedTier());
        assertEquals("m-slow", res.quotes().get(1).makerGuid());
        assertEquals(7920, res.quotes().get(1).estimatedPrintTimeSeconds());
        assertTrue(res.quotes().getFirst().totalPriceBrl().compareTo(res.quotes().get(1).totalPriceBrl()) < 0);
    }

    @Test
    void rejectsAddressOwnedByAnotherUser() {
        User caller = new User();
        caller.setId(99L);
        ServiceContext.getContext().setUser(caller);

        Address foreign = new Address();
        foreign.setGuid("addr-foreign");
        foreign.setOwnerType(EntityType.USER);
        foreign.setOwnerId(123L);
        foreign.setStatus(StatusCd.ACTIVE);
        when(addressRepository.findByGuidAndStatus("addr-foreign", StatusCd.ACTIVE)).thenReturn(Optional.of(foreign));

        InstantPrintQuoteRequest req = new InstantPrintQuoteRequest(
                "addr-foreign", null, null, null, null, null, null, null,
                List.of(new InstantPrintQuoteRequest.PlateDnaInput(
                        1, "Part", "PLA", 1, new BigDecimal("10.0"), BigDecimal.ZERO, 600,
                        new InstantPrintQuoteRequest.BoundingBoxMmInput(new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("20")),
                        null, null, null, null, null, null, null, null, null, null, null
                ))
        );

        assertThrows(ForbiddenException.class, () -> service.quoteNearbyMakers(req));
    }

    @Test
    void controllerInstantQuotesEndpointReturnsStructuredResponse() throws Exception {
        PrintPricingService legacyPricing = mock(PrintPricingService.class);
        InstantPrintQuoteService instantService = mock(InstantPrintQuoteService.class);
        PrintPricingController controller = new PrintPricingController(legacyPricing);
        controller.setInstantPrintQuoteService(instantService);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        InstantPrintQuoteResponse response = new InstantPrintQuoteResponse(
                "BRL", "bambu", "Bambu Lab A1 0.4 nozzle",
                new InstantPrintQuoteResponse.DnaSummaryDto(
                        1, 1, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"), 600,
                        new InstantPrintQuoteResponse.BoundingBoxMmDto(new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("20"))
                ),
                1, 1, List.of()
        );
        when(instantService.quoteNearbyMakers(any())).thenReturn(response);

        mvc.perform(post("/print/instant-quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": -23.6,
                                  "longitude": -46.5,
                                  "plates": [
                                    {
                                      "plateNumber": 1,
                                      "plateName": "cube.stl",
                                      "material": "PLA",
                                      "quantity": 1,
                                      "modelWeightGrams": 10.0,
                                      "supportWeightGrams": 0.0,
                                      "basePrintTimeSeconds": 600,
                                      "boundingBoxMm": { "x": 20, "y": 20, "z": 20 }
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("BRL"))
                .andExpect(jsonPath("$.data.eligibleMakersCount").value(1));
    }

    private Company maker(Long id, String guid, String name, boolean quotes, BigDecimal rating) {
        Company c = new Company();
        c.setId(id);
        c.setGuid(guid);
        c.setSlug(guid);
        c.setName(name);
        c.setStatus(StatusCd.ACTIVE);
        c.setApprovalStatus(MakerApprovalStatus.APPROVED);
        c.setVacationMode(false);
        c.setAcceptingCustomQuotes(quotes);
        c.setRatingAvg(rating);
        c.setMaterialsJson("[\"PETG\",\"PLA\"]");
        return c;
    }

    private Address cityAddr(Long ownerId, String city, String state) {
        Address a = new Address();
        a.setOwnerId(ownerId);
        a.setCity(city);
        a.setState(state);
        return a;
    }

    private MakerPricingProfile profile(Long companyId, boolean enabled, String hourly, String minFee) {
        MakerPricingProfile p = MakerPricingProfile.defaultForCompany(companyId);
        p.setInstantQuoteEnabled(enabled);
        p.setHourlyRateBrl(new BigDecimal(hourly));
        p.setMinimumOrderFeeBrl(new BigDecimal(minFee));
        p.setSetupFeeBrl(BigDecimal.ZERO);
        p.setSetupFeeScope(SetupFeeScope.PER_PLATE);
        p.setLeadTimeDays(2);
        return p;
    }

    private MakerMaterialPrice matPrice(Long companyId, MaterialType mat, String price) {
        MakerMaterialPrice mp = new MakerMaterialPrice();
        mp.setCompanyId(companyId);
        mp.setMaterial(mat);
        mp.setPricePerGramBrl(new BigDecimal(price));
        mp.setEnabled(true);
        return mp;
    }

    private Asset printerAsset(Long companyId, String guid, String model, PrinterSpeedTier tier) {
        Asset a = new Asset();
        a.setId(companyId * 100);
        a.setGuid(guid);
        a.setCompanyId(companyId);
        a.setName(model);
        a.setModel(model);
        a.setStatus(StatusCd.ACTIVE);
        a.setPrinterStatus(PrinterStatus.ONLINE);
        a.setTrayWidth(new BigDecimal("256"));
        a.setTrayDepth(new BigDecimal("256"));
        a.setTrayHeight(new BigDecimal("256"));
        a.setSpeedTier(tier);
        return a;
    }
}
