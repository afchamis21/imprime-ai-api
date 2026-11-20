package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.maker.PatchMakerProfileRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProfileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.chat.ChatSummaryResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.maker.MakerDashboardResponse;
import org.imprime.ai.api.http.response.maker.MakerStudioProfileResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerProject;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.ChatRoleFilter;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.NegotiationStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PixKeyType;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.ProjectStatus;
import org.imprime.ai.api.model.enums.ShippingOption;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerProjectRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.service.impl.MakerFinanceServiceImpl;
import org.imprime.ai.api.service.impl.MakerStudioServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerStudioServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AddressRepository addressRepository;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();
    @Mock
    private MakerFinanceServiceImpl makerFinanceService;
    @Mock
    private ChatRestService chatRestService;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private PrintQueueItemRepository printQueueItemRepository;
    @Mock
    private MakerProjectRepository projectRepository;
    @Mock
    private FilamentRepository filamentRepository;

    @InjectMocks
    private MakerStudioServiceImpl service;

    private User makerUser;
    private Company company;
    private Address address;

    @BeforeEach
    void setUp() {
        makerUser = new User();
        makerUser.setId(5L);
        makerUser.setGuid("maker-5");
        makerUser.setCompanyId(20L);

        company = new Company();
        company.setId(20L);
        company.setGuid("comp-20");
        company.setSlug("studio-impressao");
        company.setName("Studio Impressão");
        company.setPixKeyType(PixKeyType.EMAIL);
        company.setPixKey("financeiro@studio3d.com.br");
        company.setMinLeadTimeDays(2);
        company.setMaxLeadTimeDays(5);
        company.setAddressId(200L);
        company.setStatus(StatusCd.ACTIVE);

        address = new Address();
        address.setId(200L);
        address.setOwnerId(20L);
        address.setOwnerType(EntityType.COMPANY);
        address.setCity("São Paulo");
        address.setState("SP");
        address.setStatus(StatusCd.ACTIVE);

        ServiceContext.getContext().setUser(makerUser);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void getStudioProfileMasksPixKeyAndIncludesCityState() {
        when(companyRepository.findById(20L)).thenReturn(Optional.of(company));
        when(addressRepository.findById(200L)).thenReturn(Optional.of(address));

        MakerStudioProfileResponse profile = service.getStudioProfile();

        assertEquals("Studio Impressão", profile.name());
        assertEquals("fin***@studio3d.com.br", profile.pixKeyMasked());
        assertEquals("São Paulo", profile.city());
        assertEquals("SP", profile.state());
    }

    @Test
    void patchStudioProfileUpdatesOnlyProvidedFieldsAndPreservesExisting() {
        when(companyRepository.findById(20L)).thenReturn(Optional.of(company));
        when(addressRepository.findById(200L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));

        PatchMakerProfileRequest patch = new PatchMakerProfileRequest(
                null,
                null,
                "Novo Slogan",
                null, null, null,
                "Campinas",
                null, null, null,
                List.of(MakerCapability.FDM, MakerCapability.MULTICOLOR_AMS),
                null, null, null, null,
                List.of(ShippingOption.CORREIOS),
                true,
                "Retorno na segunda",
                null, null, null
        );

        MakerStudioProfileResponse updated = service.patchStudioProfile(patch);

        assertEquals("Studio Impressão", updated.name());
        assertEquals("Novo Slogan", updated.tagline());
        assertEquals("Campinas", updated.city());
        assertEquals("SP", updated.state());
        assertTrue(updated.vacationMode());
        assertEquals("Retorno na segunda", updated.vacationMessage());
        assertEquals(List.of(MakerCapability.FDM, MakerCapability.MULTICOLOR_AMS), updated.capabilities());
        assertEquals(List.of(ShippingOption.CORREIOS), updated.shippingOptions());
    }

    @Test
    void replaceStudioProfileRejectsInvalidLeadTimeRange() {
        when(companyRepository.findById(20L)).thenReturn(Optional.of(company));

        UpdateMakerProfileRequest invalidRequest = new UpdateMakerProfileRequest(
                "Studio Impressão",
                "@studio",
                null,
                "Bio válida",
                PixKeyType.EMAIL,
                "pix@studio.com",
                "São Paulo",
                "SP",
                null, null, null, 2,
                10,
                3,
                BigDecimal.TEN,
                null, false, null, true, true, null
        );

        assertThrows(BadRequestException.class, () -> service.replaceStudioProfile(invalidRequest));
        verify(companyRepository, never()).save(any());
    }

    @Test
    void getDashboardCombinesFinanceStatsChatsOrdersAndFleetSummary() {
        company.setRatingAvg(new BigDecimal("4.92"));
        MakerBriefResponse makerBrief = new MakerBriefResponse(
                "comp-20", "studio-impressao", "Studio Impressão", null, new BigDecimal("4.92"), 18, true
        );

        Asset printer1 = new Asset();
        printer1.setId(1L);
        printer1.setGuid("prt-1");
        printer1.setName("Voron 2.4");
        printer1.setPrinterStatus(PrinterStatus.PRINTING);

        Asset printer2 = new Asset();
        printer2.setId(2L);
        printer2.setGuid("prt-2");
        printer2.setName("Prusa MK4");
        printer2.setPrinterStatus(PrinterStatus.ONLINE);

        PrintQueueItem activeJob = new PrintQueueItem();
        activeJob.setGuid("job-1");
        activeJob.setAssetId(1L);
        activeJob.setTitle("Case Raspberry Pi");
        activeJob.setJobStatus(PrintJobStatus.PRINTING);
        activeJob.setProgressPercent(75);
        activeJob.setEstimatedTimeMinutes(120);

        PrintQueueItem queuedJob = new PrintQueueItem();
        queuedJob.setGuid("job-2");
        queuedJob.setAssetId(2L);
        queuedJob.setTitle("Suporte Monitor");
        queuedJob.setJobStatus(PrintJobStatus.QUEUED);

        MakerProject activeProject = new MakerProject();
        activeProject.setProjectStatus(ProjectStatus.IN_PROGRESS);
        MakerProject completedProject = new MakerProject();
        completedProject.setProjectStatus(ProjectStatus.COMPLETED);

        Filament lowStock = new Filament();
        lowStock.setStock(new BigDecimal("180.00"));
        lowStock.setLowStockThresholdGrams(new BigDecimal("250.00"));

        Filament healthyStock = new Filament();
        healthyStock.setStock(new BigDecimal("850.00"));
        healthyStock.setLowStockThresholdGrams(new BigDecimal("200.00"));

        ChatSummaryResponse chatSummary = new ChatSummaryResponse(
                UUID.randomUUID(), "ord-1", "PED-0001", "Suporte",
                MaterialType.PLA, NegotiationStatus.OPEN, new BigDecimal("120.00"),
                3, 1, "Olá", Instant.now(), null, makerBrief
        );
        OrderSummaryResponse orderSummary = new OrderSummaryResponse(
                "ord-1", "PED-0001", OrderStatusCd.IN_PRODUCTION,
                new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("120.00"),
                null, null, makerBrief, null, List.of(), Instant.now(), Instant.now()
        );

        when(companyRepository.findById(20L)).thenReturn(Optional.of(company));
        when(orderViewAssembler.toMakerBrief(company)).thenReturn(makerBrief);
        when(makerFinanceService.computeBalanceSnapshot(company))
                .thenReturn(new MakerFinanceServiceImpl.BalanceSnapshot(
                        new BigDecimal("820.00"), new BigDecimal("120.00"), 14L
                ));
        when(assetRepository.findAllByCompanyIdAndStatus(20L, StatusCd.ACTIVE))
                .thenReturn(List.of(printer1, printer2));
        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(20L, StatusCd.ACTIVE))
                .thenReturn(List.of(activeJob, queuedJob));
        when(projectRepository.findAllByCompanyIdAndStatus(20L, StatusCd.ACTIVE))
                .thenReturn(List.of(activeProject, completedProject));
        when(filamentRepository.findAllByCompanyIdAndStatus(20L, StatusCd.ACTIVE))
                .thenReturn(List.of(lowStock, healthyStock));
        when(chatRestService.listChats(ChatRoleFilter.MAKER, null, null, PageRequest.of(0, 5)))
                .thenReturn(new PageResponse<>(List.of(chatSummary), 0, 5, 1, 1, true, true));
        List<Order> vendorOrders = List.of(new Order());
        when(orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(20L, StatusCd.ACTIVE))
                .thenReturn(vendorOrders);
        when(orderViewAssembler.filterAndPaginate(eq(vendorOrders), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 5))))
                .thenReturn(new PageResponse<>(List.of(orderSummary), 0, 5, 1, 1, true, true));

        MakerDashboardResponse dashboard = service.getDashboard();

        assertEquals("Studio Impressão", dashboard.makerInfo().name());
        assertEquals(new BigDecimal("820.00"), dashboard.stats().availableBalance());
        assertEquals(new BigDecimal("120.00"), dashboard.stats().pendingBalance());
        assertEquals(14L, dashboard.stats().totalSales());
        assertEquals(1, dashboard.stats().activePrints());
        assertEquals(new BigDecimal("4.92"), dashboard.stats().averageRating());
        assertEquals(1, dashboard.activeChats().size());
        assertEquals(1, dashboard.recentOrders().size());
        assertEquals(2, dashboard.fleetSummary().totalPrinters());
        assertEquals(2, dashboard.fleetSummary().onlinePrinters());
        assertEquals(1, dashboard.fleetSummary().printingPrinters());
        assertEquals(1, dashboard.fleetSummary().queuedJobs());
        assertEquals(1, dashboard.fleetSummary().activeProjects());
        assertEquals(1, dashboard.fleetSummary().lowStockFilaments());
        assertNotNull(dashboard.fleetSummary().activeJob());
        assertEquals("Voron 2.4", dashboard.fleetSummary().activeJob().printerName());
        assertEquals(30, dashboard.fleetSummary().activeJob().etaMinutes());
    }
}
