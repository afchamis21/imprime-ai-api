package org.imprime.ai.api.service;

import org.imprime.ai.api.controller.maker.MakerMarketplacePricingController;
import org.imprime.ai.api.http.request.maker.UpdateMakerMarketplacePricingRequest;
import org.imprime.ai.api.http.response.maker.MakerMarketplacePricingResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerMaterialPrice;
import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.SetupFeeScope;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerMaterialPriceRepository;
import org.imprime.ai.api.repo.db.MakerPricingProfileRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.service.impl.MakerMarketplacePricingServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MakerMarketplacePricingServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private MakerPricingProfileRepository pricingProfileRepository;
    @Mock
    private MakerMaterialPriceRepository materialPriceRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private PrinterCatalogRepository printerCatalogRepository;
    @Mock
    private CompanyRepository companyRepository;

    private JsonCodec jsonCodec;
    private MakerMarketplacePricingServiceImpl service;
    private Company company;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
        service = new MakerMarketplacePricingServiceImpl(
                makerCompanyResolver,
                pricingProfileRepository,
                materialPriceRepository,
                assetRepository,
                printerCatalogRepository,
                companyRepository,
                new PrinterSpeedResolver(),
                jsonCodec
        );
        company = new Company();
        company.setId(42L);
        company.setGuid("comp-42");
        company.setMinOrderValue(new BigDecimal("18.00"));
        company.setMinLeadTimeDays(2);
        company.setMaxLeadTimeDays(4);
        company.setMaterialsJson("[\"PLA\",\"PETG\"]");
    }

    @Test
    void getMyPricingDerivesInitialMaterialsAndPrinterSpeedPreview() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(pricingProfileRepository.findByCompanyIdAndStatus(42L, StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(materialPriceRepository.findAllByCompanyIdAndStatusOrderByMaterialAsc(42L, StatusCd.ACTIVE)).thenReturn(List.of());

        Asset a1 = new Asset();
        a1.setGuid("prt-a1");
        a1.setName("A1 #1");
        a1.setModel("Bambu Lab A1");
        a1.setSpeedTier(PrinterSpeedTier.HIGH_SPEED);
        a1.setTrayWidth(new BigDecimal("256"));
        a1.setTrayDepth(new BigDecimal("256"));
        a1.setTrayHeight(new BigDecimal("256"));
        when(assetRepository.findAllByCompanyIdAndStatus(42L, StatusCd.ACTIVE)).thenReturn(List.of(a1));

        MakerMarketplacePricingResponse res = service.getMyPricing();

        assertEquals(new BigDecimal("18.00"), res.minimumOrderFeeBrl());
        assertEquals(2, res.materialPrices().size());
        assertEquals(1, res.printersPreview().size());
        assertEquals(PrinterSpeedTier.HIGH_SPEED, res.printersPreview().getFirst().speedTier());
        assertEquals(new BigDecimal("1.00"), res.printersPreview().getFirst().speedMultiplier());
    }

    @Test
    void updateMyPricingUpsertsMaterialsDisablesRemovedAndSyncsCompany() {
        MakerMaterialPrice existingAbs = new MakerMaterialPrice();
        existingAbs.setCompanyId(42L);
        existingAbs.setMaterial(MaterialType.ABS);
        existingAbs.setPricePerGramBrl(new BigDecimal("0.3000"));
        existingAbs.setEnabled(true);
        existingAbs.setStatus(StatusCd.ACTIVE);

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(pricingProfileRepository.findByCompanyIdAndStatus(42L, StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(pricingProfileRepository.save(any(MakerPricingProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(materialPriceRepository.findAllByCompanyIdAndStatusOrderByMaterialAsc(42L, StatusCd.ACTIVE))
                .thenReturn(List.of(existingAbs));
        when(materialPriceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.findAllByCompanyIdAndStatus(42L, StatusCd.ACTIVE)).thenReturn(List.of());

        UpdateMakerMarketplacePricingRequest req = new UpdateMakerMarketplacePricingRequest(
                true,
                new BigDecimal("5.50"),
                new BigDecimal("25.00"),
                new BigDecimal("6.00"),
                SetupFeeScope.PER_PLATE,
                new BigDecimal("7.00"),
                new BigDecimal("0.0400"),
                6,
                new BigDecimal("1.25"),
                new BigDecimal("1.15"),
                new BigDecimal("1.25"),
                new BigDecimal("1.35"),
                List.of(new UpdateMakerMarketplacePricingRequest.MaterialPriceInput(
                        MaterialType.PETG, new BigDecimal("0.2800"), true, "Petg premium"
                ))
        );

        MakerMarketplacePricingResponse updated = service.updateMyPricing(req);

        assertEquals(new BigDecimal("25.00"), company.getMinOrderValue());
        assertEquals(6, company.getMinLeadTimeDays());
        assertEquals(6, company.getMaxLeadTimeDays());
        assertTrue(company.getMaterialsJson().contains("PETG"));
        assertFalse(Boolean.TRUE.equals(existingAbs.getEnabled()));
        assertEquals(new BigDecimal("5.50"), updated.hourlyRateBrl());
    }

    @Test
    void controllerGetAndPutEndpointsReturnOk() throws Exception {
        MakerMarketplacePricingService mockService = mock(MakerMarketplacePricingService.class);
        MakerMarketplacePricingController controller = new MakerMarketplacePricingController(mockService);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MakerMarketplacePricingResponse sample = new MakerMarketplacePricingResponse(
                true, new BigDecimal("5.00"), new BigDecimal("20.00"), new BigDecimal("4.00"),
                SetupFeeScope.PER_PLATE, new BigDecimal("6.00"), new BigDecimal("0.0500"),
                3, new BigDecimal("1.20"), new BigDecimal("1.15"), new BigDecimal("1.25"), new BigDecimal("1.35"),
                List.of(new MakerMarketplacePricingResponse.MaterialPriceItem(MaterialType.PLA, new BigDecimal("0.2200"), true, null)),
                List.of()
        );
        when(mockService.getMyPricing()).thenReturn(sample);
        when(mockService.updateMyPricing(any())).thenReturn(sample);

        mvc.perform(get("/maker/marketplace-pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hourlyRateBrl").value(5.00));

        mvc.perform(put("/maker/marketplace-pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instantQuoteEnabled": true,
                                  "hourlyRateBrl": 5.00,
                                  "minimumOrderFeeBrl": 20.00,
                                  "setupFeeBrl": 4.00,
                                  "setupFeeScope": "PER_PLATE",
                                  "supportRemovalFeeBrl": 6.00,
                                  "supportPricePerGramBrl": 0.05,
                                  "leadTimeDays": 3,
                                  "materialPrices": [
                                    {"material":"PLA","pricePerGramBrl":0.22,"enabled":true}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minimumOrderFeeBrl").value(20.00));
    }
}
