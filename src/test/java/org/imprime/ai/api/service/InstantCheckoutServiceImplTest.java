package org.imprime.ai.api.service;

import org.imprime.ai.api.config.SlicingValidationProperties;
import org.imprime.ai.api.config.SlicingValidationProperties.ExecutionMode;
import org.imprime.ai.api.controller.InstantCheckoutController;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.InstantCheckoutRequest;
import org.imprime.ai.api.http.response.order.InstantCheckoutResponse;
import org.imprime.ai.api.http.response.order.SlicingValidationStatusResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.MakerMaterialPrice;
import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderPricingSource;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.SetupFeeScope;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.UnprocessableEntityException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.MakerMaterialPriceRepository;
import org.imprime.ai.api.repo.db.MakerPricingProfileRepository;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.service.impl.InstantCheckoutServiceImpl;
import org.imprime.ai.api.slicing.BambuProjectPacker;
import org.imprime.ai.api.slicing.HeadlessSlicerEngine.HeadlessSliceResult;
import org.imprime.ai.api.slicing.HeadlessSlicerEngineRouter;
import org.imprime.ai.api.slicing.SlicedArtifactStorage;
import org.imprime.ai.api.slicing.SlicingReferenceProfileResolver;
import org.imprime.ai.api.slicing.SlicingValidationComparator;
import org.imprime.ai.api.slicing.SlicingValidationQueueMessage;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.validator.PrintFileValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InstantCheckoutServiceImplTest {

    @Mock private PrintFileValidator printFileValidator;
    @Mock private LegalDocumentService legalDocuments;
    @Mock private OrderUploadRequestService uploadRequests;
    @Mock private AddressRepository addressRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private MakerPricingProfileRepository pricingProfileRepository;
    @Mock private MakerMaterialPriceRepository materialPriceRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private PrinterCatalogRepository printerCatalogRepository;
    @Mock private FileAssetService fileAssetService;
    @Mock private FileAssetRepository fileAssetRepository;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderFileAssetRepository orderFileAssetRepository;
    @Mock private OrderStatusService orderStatusService;
    @Mock private HeadlessSlicerEngineRouter slicerRouter;
    @Mock private SlicedArtifactStorage artifactStorage;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    private SlicingValidationProperties properties;
    private InstantCheckoutServiceImpl service;
    private User buyer;
    private Address address;
    private Company maker;
    private Order savedOrder;
    private OrderFileAsset savedPlate;

    @BeforeEach
    void setUp() {
        JsonCodec jsonCodec = new JsonCodec();
        properties = new SlicingValidationProperties();
        properties.setExecutionMode(ExecutionMode.SYNC);

        service = new InstantCheckoutServiceImpl(
                properties,
                printFileValidator,
                legalDocuments,
                uploadRequests,
                addressRepository,
                companyRepository,
                pricingProfileRepository,
                materialPriceRepository,
                assetRepository,
                printerCatalogRepository,
                fileAssetService,
                fileAssetRepository,
                objectStorageService,
                orderRepository,
                orderFileAssetRepository,
                orderStatusService,
                new MakerEligibilityService(jsonCodec),
                new PrinterSpeedResolver(),
                new MarketplaceQuoteCalculator(),
                new BambuProjectPacker(),
                new SlicingReferenceProfileResolver(properties, jsonCodec),
                slicerRouter,
                new SlicingValidationComparator(),
                artifactStorage,
                jsonCodec,
                rabbitTemplateProvider
        );

        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-1");
        ServiceContext.getContext().setUser(buyer);

        address = new Address();
        address.setId(10L);
        address.setGuid("addr-10");
        address.setOwnerType(EntityType.USER);
        address.setOwnerId(1L);
        address.setStatus(StatusCd.ACTIVE);

        maker = new Company();
        maker.setId(20L);
        maker.setGuid("maker-20");
        maker.setName("Maker Alpha");
        maker.setStatus(StatusCd.ACTIVE);
        maker.setApprovalStatus(MakerApprovalStatus.APPROVED);
        maker.setVacationMode(false);
        maker.setAcceptingCustomQuotes(true);
        maker.setMaterialsJson("[\"PLA\",\"PETG\"]");
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    private void stubCommonCheckoutDependencies() {
        when(legalDocuments.requireAcceptableVersion("print-declaration", "2026-01")).thenReturn(100L);
        when(legalDocuments.requireAcceptableVersion("prohibited-items", "2026-01")).thenReturn(101L);
        when(addressRepository.findByGuidAndStatus("addr-10", StatusCd.ACTIVE)).thenReturn(Optional.of(address));
        when(companyRepository.findByGuidAndStatus("maker-20", StatusCd.ACTIVE)).thenReturn(Optional.of(maker));

        MakerPricingProfile profile = MakerPricingProfile.defaultForCompany(20L);
        profile.setHourlyRateBrl(new BigDecimal("6.00"));
        profile.setMinimumOrderFeeBrl(new BigDecimal("10.00"));
        profile.setSetupFeeBrl(BigDecimal.ZERO);
        profile.setSetupFeeScope(SetupFeeScope.PER_PLATE);
        when(pricingProfileRepository.findByCompanyIdAndStatus(20L, StatusCd.ACTIVE)).thenReturn(Optional.of(profile));

        MakerMaterialPrice mp = new MakerMaterialPrice();
        mp.setCompanyId(20L);
        mp.setMaterial(MaterialType.PLA);
        mp.setPricePerGramBrl(new BigDecimal("0.2000"));
        mp.setEnabled(true);
        when(materialPriceRepository.findAllByCompanyIdAndStatusOrderByMaterialAsc(20L, StatusCd.ACTIVE))
                .thenReturn(List.of(mp));

        Asset a1 = new Asset();
        a1.setId(30L);
        a1.setGuid("prt-30");
        a1.setCompanyId(20L);
        a1.setName("Bambu A1");
        a1.setModel("Bambu Lab A1");
        a1.setStatus(StatusCd.ACTIVE);
        a1.setPrinterStatus(PrinterStatus.ONLINE);
        a1.setTrayWidth(new BigDecimal("256"));
        a1.setTrayDepth(new BigDecimal("256"));
        a1.setTrayHeight(new BigDecimal("256"));
        a1.setSpeedTier(PrinterSpeedTier.HIGH_SPEED);
        when(assetRepository.findAllByCompanyIdAndStatus(20L, StatusCd.ACTIVE)).thenReturn(List.of(a1));

        FileAsset uploaded = new FileAsset();
        uploaded.setId(50L);
        uploaded.setGuid("fa-50");
        uploaded.setName("cube.stl");
        when(fileAssetService.uploadOrderModel(any())).thenReturn(uploaded);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(500L);
                o.setGuid("ord-500");
            }
            savedOrder = o;
            return o;
        });
        when(orderFileAssetRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<OrderFileAsset> list = inv.getArgument(0);
            for (OrderFileAsset p : list) {
                if (p.getId() == null) {
                    p.setId(600L);
                }
                savedPlate = p;
            }
            return list;
        });
    }

    @Test
    void validatesWithinToleranceMarksOrderConfirmedAndStoresArtifact() {
        stubCommonCheckoutDependencies();
        when(slicerRouter.slice(any())).thenReturn(new HeadlessSliceResult(
                new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"),
                3600, null, null, "BAMBU_STUDIO_CLI"
        ));
        when(artifactStorage.storeValidated3mf(eq("ord-500"), eq(1), eq(1L), any())).thenReturn(901L);

        InstantCheckoutRequest req = sampleRequest(new BigDecimal("50.00"), BigDecimal.ZERO, 3600, new BigDecimal("16.00"));
        InstantCheckoutResponse res = service.createAndValidate(List.of(cubeMultipart()), req, null);

        assertEquals(SlicingValidationStatus.VALIDATED, res.validationStatus());
        assertTrue(res.checkoutReady());
        assertFalse(res.requiresPriceConfirmation());
        assertEquals(new BigDecimal("16.00"), res.serverValidatedAmount()); // 50*0.2 + 1h*6 = 16.00
        assertEquals(901L, savedPlate.getValidated3mfFileAssetId());
        verify(orderStatusService).move(any(Order.class), eq(OrderStatusCd.CONFIRMED));
    }

    @Test
    void recalculatesWhenModerateDivergenceDetectedAndConfirmsExplicitly() {
        stubCommonCheckoutDependencies();
        // Client sends 85g (total ~22.67), server slices 100g + 4200s -> 27.00 (17.6% weight divergence -> RECALCULATED)
        when(slicerRouter.slice(any())).thenReturn(new HeadlessSliceResult(
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                4200, null, null, "BAMBU_STUDIO_CLI"
        ));

        InstantCheckoutRequest req = sampleRequest(new BigDecimal("85.00"), BigDecimal.ZERO, 3900, new BigDecimal("23.50"));
        InstantCheckoutResponse res = service.createAndValidate(List.of(cubeMultipart()), req, null);

        assertEquals(SlicingValidationStatus.RECALCULATED, res.validationStatus());
        assertFalse(res.checkoutReady());
        assertTrue(res.requiresPriceConfirmation());
        assertEquals(new BigDecimal("27.00"), res.serverValidatedAmount());
        verify(orderStatusService, never()).move(any(Order.class), eq(OrderStatusCd.CONFIRMED));

        // Now confirm recalculated quote
        when(orderRepository.findLockedByGuid("ord-500")).thenReturn(Optional.of(savedOrder));
        when(orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(500L, StatusCd.ACTIVE))
                .thenReturn(List.of(savedPlate));

        SlicingValidationStatusResponse confirmed = service.confirmRecalculatedQuote("ord-500");
        assertEquals(SlicingValidationStatus.VALIDATED, confirmed.validationStatus());
        assertTrue(confirmed.checkoutReady());
        assertEquals(new BigDecimal("27.00"), savedOrder.getOrderTotal());
        verify(orderStatusService).move(savedOrder, OrderStatusCd.CONFIRMED);
    }

    @Test
    void rejectsTamperedPayloadAboveHardRejectThreshold() {
        stubCommonCheckoutDependencies();
        // Client claims 20g / 600s, server slices 100g / 3600s (>30% divergence -> 422 REJECTED)
        when(slicerRouter.slice(any())).thenReturn(new HeadlessSliceResult(
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                3600, null, null, "BAMBU_STUDIO_CLI"
        ));

        InstantCheckoutRequest req = sampleRequest(new BigDecimal("20.00"), BigDecimal.ZERO, 600, new BigDecimal("5.00"));

        UnprocessableEntityException ex = assertThrows(
                UnprocessableEntityException.class,
                () -> service.createAndValidate(List.of(cubeMultipart()), req, null)
        );
        assertEquals(MessageCd.SLICE_DIVERGENCE_EXCEEDED, ex.getMessageCd());
        assertEquals(SlicingValidationStatus.REJECTED, savedOrder.getSlicingValidationStatus());
    }

    @Test
    void enqueuesRabbitMqAndReturnsPendingInAsyncMode() {
        properties.setExecutionMode(ExecutionMode.ASYNC);
        stubCommonCheckoutDependencies();
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);

        InstantCheckoutRequest req = sampleRequest(new BigDecimal("50.00"), BigDecimal.ZERO, 3600, new BigDecimal("16.00"));
        InstantCheckoutResponse res = service.createAndValidate(List.of(cubeMultipart()), req, null);

        assertEquals(SlicingValidationStatus.PENDING, res.validationStatus());
        assertFalse(res.checkoutReady());
        assertEquals("/orders/ord-500/slicing-validation", res.pollUrl());
        verify(rabbitTemplate).convertAndSend(
                eq(properties.getRabbitmq().getExchange()),
                eq(properties.getRabbitmq().getRoutingKey()),
                any(SlicingValidationQueueMessage.class)
        );
        verifyNoInteractions(slicerRouter);
    }

    @Test
    void controllerMaps201_202_200AndActionsProperly() throws Exception {
        InstantCheckoutService mockSvc = mock(InstantCheckoutService.class);
        InstantCheckoutController controller = new InstantCheckoutController(mockSvc);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        InstantCheckoutResponse validated = new InstantCheckoutResponse(
                "ord-1", "PED-1", "m1", "Maker 1",
                SlicingValidationStatus.VALIDATED, true, false,
                new BigDecimal("25.00"), new BigDecimal("25.00"),
                "A1", PrinterSpeedTier.HIGH_SPEED, new BigDecimal("1.00"),
                1200, 3, "/orders/ord-1/slicing-validation", "OK", List.of()
        );
        InstantCheckoutResponse pending = new InstantCheckoutResponse(
                "ord-2", "PED-2", "m1", "Maker 1",
                SlicingValidationStatus.PENDING, false, false,
                new BigDecimal("25.00"), null,
                "A1", PrinterSpeedTier.HIGH_SPEED, new BigDecimal("1.00"),
                1200, 3, "/orders/ord-2/slicing-validation", "Pending", List.of()
        );

        String jsonPayload = """
                {
                  "makerGuid": "maker-20",
                  "addressGuid": "addr-10",
                  "rightsDeclared": true,
                  "declarationVersion": "2026-01",
                  "prohibitedItemsVersion": "2026-01",
                  "plates": [
                    {
                      "plateNumber": 1,
                      "material": "PLA",
                      "quantity": 1,
                      "clientModelWeightGrams": 50.0,
                      "clientBasePrintTimeSeconds": 3600
                    }
                  ]
                }
                """;
        MockMultipartFile payloadPart = new MockMultipartFile(
                "payload", "", MediaType.APPLICATION_JSON_VALUE, jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        when(mockSvc.createAndValidate(anyList(), any(), any())).thenReturn(validated);
        mvc.perform(multipart("/orders/instant-checkout").file(cubeMultipart()).file(payloadPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderGuid").value("ord-1"));

        when(mockSvc.createAndValidate(anyList(), any(), any())).thenReturn(pending);
        mvc.perform(multipart("/orders/instant-checkout").file(cubeMultipart()).file(payloadPart))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.pollUrl").value("/orders/ord-2/slicing-validation"));

        SlicingValidationStatusResponse statusRes = new SlicingValidationStatusResponse(
                "ord-1", "PED-1", SlicingValidationStatus.VALIDATED, true, false,
                new BigDecimal("25.00"), new BigDecimal("25.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                "A1", PrinterSpeedTier.HIGH_SPEED, new BigDecimal("1.00"),
                1200, 3, true, "/orders/ord-1/slicing-validation", null, "OK", List.of()
        );
        when(mockSvc.getValidationStatus("ord-1")).thenReturn(statusRes);
        when(mockSvc.confirmRecalculatedQuote("ord-1")).thenReturn(statusRes);

        mvc.perform(get("/orders/ord-1/slicing-validation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutReady").value(true));

        mvc.perform(post("/orders/ord-1/confirm-recalculated-quote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validationStatus").value("VALIDATED"));
    }

    private InstantCheckoutRequest sampleRequest(BigDecimal modelG, BigDecimal supG, int baseSec, BigDecimal expectedTotal) {
        return new InstantCheckoutRequest(
                "maker-20",
                "addr-10",
                true,
                "2026-01",
                "2026-01",
                "bambu",
                "Bambu Lab A1 0.4 nozzle",
                expectedTotal,
                List.of(new InstantCheckoutRequest.PlateCheckoutInput(
                        1, "cube.stl", "PLA", "#18181b", List.of("#18181b"),
                        1, 1.0, 1.0, 1.0,
                        new BigDecimal("20.00"), new BigDecimal("20.00"), new BigDecimal("20.00"),
                        new BigDecimal("0.20"), 20, "gyroid", 2,
                        false, "buildplate", "none", "raw", "standard", "normal",
                        false, 1, null,
                        modelG, supG, baseSec
                ))
        );
    }

    private MockMultipartFile cubeMultipart() {
        float s = 20.0f;
        float[][] pts = {
                {0, 0, 0}, {s, 0, 0}, {s, s, 0}, {0, s, 0},
                {0, 0, s}, {s, 0, s}, {s, s, s}, {0, s, s}
        };
        int[][] faces = {
                {0, 2, 1}, {0, 3, 2}, {4, 5, 6}, {4, 6, 7},
                {0, 1, 5}, {0, 5, 4}, {3, 7, 6}, {3, 6, 2},
                {0, 4, 7}, {0, 7, 3}, {1, 2, 6}, {1, 6, 5}
        };
        ByteBuffer buf = ByteBuffer.allocate(84 + faces.length * 50).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(80);
        buf.putInt(faces.length);
        for (int[] f : faces) {
            buf.putFloat(0).putFloat(0).putFloat(1);
            for (int vi : f) {
                buf.putFloat(pts[vi][0]).putFloat(pts[vi][1]).putFloat(pts[vi][2]);
            }
            buf.putShort((short) 0);
        }
        return new MockMultipartFile("file", "cube.stl", "application/octet-stream", buf.array());
    }
}
