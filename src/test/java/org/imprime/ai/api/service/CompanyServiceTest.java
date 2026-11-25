package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.http.request.company.RegisterMakerPrinterRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.CompanyAndAddress;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.cache.MakerPresenceCache;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyDAO;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private AddressService addressService;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyDAO companyDAO;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private PrinterCatalogRepository printerCatalogRepository;

    private JsonCodec jsonCodec;
    private MakerPresenceCache makerPresenceCache;
    private MakerEligibilityService makerEligibilityService;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
        makerPresenceCache = new MakerPresenceCache(Duration.ofMinutes(4), 1_000L);
        makerEligibilityService = new MakerEligibilityService(jsonCodec);
        companyService = new CompanyService(
                addressService,
                companyRepository,
                companyDAO,
                makerPresenceCache,
                assetRepository,
                printerCatalogRepository,
                makerEligibilityService,
                jsonCodec
        );
    }

    @Test
    void registerCompanyCreatesCatalogAssetsAndPersistsMakerTechProfile() {
        PrinterCatalog a1 = new PrinterCatalog();
        a1.setId(10L);
        a1.setGuid("pc-bambu-a1");
        a1.setBrand("Bambu Lab");
        a1.setModelName("A1");
        a1.setTechnology(PrinterTechnology.FDM);
        a1.setBuildVolumeXMm(new BigDecimal("256.00"));
        a1.setBuildVolumeYMm(new BigDecimal("256.00"));
        a1.setBuildVolumeZMm(new BigDecimal("256.00"));
        a1.setDefaultNozzleMm(new BigDecimal("0.400"));
        a1.setAvgPowerWatts(110);
        a1.setEstimatedPriceBrl(new BigDecimal("3490.00"));
        a1.setSupportsMulticolor(true);
        a1.setMaxColorSlots(4);
        a1.setReleaseYear(2023);
        a1.setSupportedMaterials(new LinkedHashSet<>(Set.of(MaterialType.PLA, MaterialType.PETG, MaterialType.TPU)));

        User owner = new User();
        owner.setId(7L);
        owner.setAvatarUrl("https://cdn.example/avatar.png");

        when(companyRepository.existsByName("Lab 3D")).thenReturn(false);
        when(companyRepository.existsByDocumentTypeAndDocument(DocumentType.CNPJ, "12345678000195")).thenReturn(false);
        when(companyRepository.existsBySlug(any())).thenReturn(false);
        when(printerCatalogRepository.findByGuidAndStatus("pc-bambu-a1", StatusCd.ACTIVE)).thenReturn(Optional.of(a1));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(100L);
            }
            return c;
        });

        Address companyAddr = new Address();
        companyAddr.setId(55L);
        when(addressService.registerAddress(any(), eq(EntityType.COMPANY), eq(100L), eq(true))).thenReturn(companyAddr);

        RegisterCompanyRequest request = new RegisterCompanyRequest(
                "Lab 3D",
                DocumentType.CNPJ,
                "12345678000195",
                new RegisterAddressRequest("01310100", "Bela Vista", "São Paulo", "SP", "BR", "Av Paulista 100", null, -23.56, -46.65),
                List.of(new RegisterMakerPrinterRequest("pc-bambu-a1", "A1 Bancada", 2, true, 4, null, null)),
                null,
                true,
                true,
                4,
                null,
                true,
                false,
                true,
                false,
                new BigDecimal("0.08"),
                "Especializado em protótipos coloridos"
        );

        Company saved = companyService.registerCompany(request, owner);

        assertEquals(10L, saved.getPrimaryPrinterCatalogId());
        assertEquals("Bambu Lab A1", saved.getPrimaryPrinter());
        assertTrue(saved.getSupportsMulticolor());
        assertEquals(4, saved.getMaxMulticolorSlots());
        assertTrue(saved.getAcceptingCustomQuotes());
        assertTrue(saved.getOffersPostProcessing());
        assertTrue(saved.getOffersCadModeling());
        assertFalse(saved.getOffersPainting());
        assertEquals(55L, saved.getAddressId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Asset>> assetsCaptor = ArgumentCaptor.forClass(List.class);
        verify(assetRepository).saveAll(assetsCaptor.capture());
        List<Asset> createdAssets = assetsCaptor.getValue();
        assertEquals(2, createdAssets.size());
        assertEquals("A1 Bancada #1", createdAssets.get(0).getName());
        assertEquals("A1 Bancada #2", createdAssets.get(1).getName());
        assertEquals(10L, createdAssets.get(0).getPrinterCatalogId());
        assertTrue(createdAssets.get(0).getSupportsMulticolor());
    }

    @Test
    void findCompaniesForOrderFiltersIncompatibleMakersAndEnrichesChoices() {
        Address buyerAddr = new Address();
        buyerAddr.setLatitude(-23.55);
        buyerAddr.setLongitude(-46.63);

        Company monoSmall = new Company();
        monoSmall.setId(1L);
        monoSmall.setGuid("m-1");
        monoSmall.setName("Mono Small");
        monoSmall.setStatus(StatusCd.ACTIVE);
        monoSmall.setApprovalStatus(MakerApprovalStatus.APPROVED);
        monoSmall.setAcceptingCustomQuotes(true);
        monoSmall.setVacationMode(false);
        monoSmall.setSupportsMulticolor(false);
        monoSmall.setMaterialsJson("[\"PLA\"]");

        Company multiBig = new Company();
        multiBig.setId(2L);
        multiBig.setGuid("m-2");
        multiBig.setName("Multi Big");
        multiBig.setPrimaryPrinter("Bambu Lab A1");
        multiBig.setStatus(StatusCd.ACTIVE);
        multiBig.setApprovalStatus(MakerApprovalStatus.APPROVED);
        multiBig.setAcceptingCustomQuotes(true);
        multiBig.setVacationMode(false);
        multiBig.setSupportsMulticolor(true);
        multiBig.setMaxMulticolorSlots(4);
        multiBig.setMaterialsJson("[\"PLA\",\"PETG\"]");

        Address addr1 = new Address();
        addr1.setCity("São Paulo");
        addr1.setState("SP");
        Address addr2 = new Address();
        addr2.setCity("São Paulo");
        addr2.setState("SP");

        when(companyDAO.findNearestCompaniesByAddress(-23.55, -46.63, 100)).thenReturn(List.of(
                new CompanyAndAddress(monoSmall, addr1, 800.0),
                new CompanyAndAddress(multiBig, addr2, 2400.0)
        ));

        Asset a1Mono = new Asset();
        a1Mono.setCompanyId(1L);
        a1Mono.setStatus(StatusCd.ACTIVE);
        a1Mono.setTrayWidth(new BigDecimal("180"));
        a1Mono.setTrayDepth(new BigDecimal("180"));
        a1Mono.setTrayHeight(new BigDecimal("180"));
        a1Mono.setSupportsMulticolor(false);

        Asset a2Multi = new Asset();
        a2Multi.setCompanyId(2L);
        a2Multi.setStatus(StatusCd.ACTIVE);
        a2Multi.setTrayWidth(new BigDecimal("256"));
        a2Multi.setTrayDepth(new BigDecimal("256"));
        a2Multi.setTrayHeight(new BigDecimal("256"));
        a2Multi.setSupportsMulticolor(true);
        a2Multi.setMulticolorSlots(4);

        when(assetRepository.findAllByCompanyIdInAndStatus(List.of(1L, 2L), StatusCd.ACTIVE))
                .thenReturn(List.of(a1Mono, a2Multi));

        OrderFileAsset plate = new OrderFileAsset();
        plate.setDimensionXMm(new BigDecimal("210"));
        plate.setDimensionYMm(new BigDecimal("200"));
        plate.setDimensionZMm(new BigDecimal("120"));
        plate.setIsMultiColor(true);
        plate.setColorCount(3);
        plate.setMaterial("PLA");

        List<CompanyChoiceDTO> choices = companyService.findCompaniesForOrder(buyerAddr, List.of(plate), 20);

        assertEquals(1, choices.size());
        CompanyChoiceDTO chosen = choices.getFirst();
        assertEquals("m-2", chosen.guid());
        assertEquals("Bambu Lab A1", chosen.primaryPrinter());
        assertTrue(chosen.supportsMulticolor());
        assertEquals(4, chosen.maxMulticolorSlots());
        assertEquals(256, chosen.maxBuildVolume().xMm());
        assertEquals("km", chosen.distance().unit());
    }
}
