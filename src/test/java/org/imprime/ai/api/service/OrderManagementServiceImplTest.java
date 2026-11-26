package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.http.response.order.OrderFileResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.impl.OrderManagementServiceImpl;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderManagementServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Mock
    private OrderFileAssetRepository orderFileAssetRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private MakerOrderSupport makerOrderSupport;
    @Mock
    private org.imprime.ai.api.service.MakerOrderComplianceService makerCompliance;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private OrderManagementServiceImpl service;

    private User buyer;
    private User makerUser;
    private User outsider;
    private Order order;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-guid");

        makerUser = new User();
        makerUser.setId(2L);
        makerUser.setGuid("maker-user-guid");
        makerUser.setCompanyId(50L);

        outsider = new User();
        outsider.setId(3L);
        outsider.setGuid("outsider-guid");

        order = new Order();
        order.setId(10L);
        order.setGuid("order-guid-10");
        order.setBuyerId(1L);
        order.setVendorId(50L);
        order.setOrderTotal(new BigDecimal("120.00"));
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void getOrderDetailAllowsMakerParticipatingInQuotingChat() {
        order.setVendorId(null);
        ServiceContext.getContext().setUser(makerUser);

        OrderStatus quotingStatus = new OrderStatus();
        quotingStatus.setStatusCd(OrderStatusCd.QUOTING);

        Company company = new Company();
        company.setId(50L);
        company.setGuid("company-guid-50");

        OrderDetailResponse expected = new OrderDetailResponse(
                "order-guid-10", "PED-ORDERG", OrderStatusCd.QUOTING,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, List.of(), List.of(), Instant.now(), Instant.now()
        );

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quotingStatus);
        when(companyRepository.findById(50L)).thenReturn(Optional.of(company));
        when(chatRepository.existsByOrderGuidAndCompanyGuid("order-guid-10", "company-guid-50")).thenReturn(true);
        when(orderViewAssembler.toDetailResponse(order)).thenReturn(expected);

        OrderDetailResponse response = service.getOrderDetail("order-guid-10");

        assertEquals("order-guid-10", response.orderGuid());
    }

    @Test
    void getOrderDetailRejectsOutsiderWithForbidden() {
        ServiceContext.getContext().setUser(outsider);
        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> service.getOrderDetail("order-guid-10"));
    }

    @Test
    void getOrderDetailThrowsNotFoundWhenMissing() {
        ServiceContext.getContext().setUser(buyer);
        when(orderRepository.findOrderByGuid("missing-order")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getOrderDetail("missing-order"));
    }

    @Test
    void updateOrderStatusAllowsBuyerToCancelConfirmedOrder() {
        ServiceContext.getContext().setUser(buyer);
        OrderStatus confirmed = new OrderStatus();
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);

        when(orderRepository.findLockedByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(confirmed);
        when(orderStatusService.canMoveTo(OrderStatusCd.CONFIRMED, OrderStatusCd.CANCELLED)).thenReturn(true);

        service.updateOrderStatus("order-guid-10", new UpdateOrderStatusRequest(OrderStatusCd.CANCELLED, "Mudança de planos", null));

        assertEquals("Mudança de planos", order.getCancellationReason());
        verify(orderRepository).save(order);
        verify(orderStatusService).move(order, OrderStatusCd.CANCELLED);
        verify(orderViewAssembler).toDetailResponse(order);
    }

    @Test
    void updateOrderStatusPersistsTrackingCodeWhenMovingToInTransit() {
        ServiceContext.getContext().setUser(makerUser);
        OrderStatus readyToShip = new OrderStatus();
        readyToShip.setStatusCd(OrderStatusCd.READY_TO_SHIP);
        order.setExternalShipmentId("me-shipment-123");

        when(orderRepository.findLockedByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(readyToShip);
        when(orderStatusService.canMoveTo(OrderStatusCd.READY_TO_SHIP, OrderStatusCd.IN_TRANSIT)).thenReturn(true);

        service.updateOrderStatus("order-guid-10", new UpdateOrderStatusRequest(OrderStatusCd.IN_TRANSIT, null, "BR123456789BR"));

        assertEquals("BR123456789BR", order.getTrackingCode());
        verify(orderRepository).save(order);
        verify(orderStatusService).move(order, OrderStatusCd.IN_TRANSIT);
    }

    @Test
    void updateOrderStatusRejectsBuyerStartingProduction() {
        ServiceContext.getContext().setUser(buyer);
        OrderStatus confirmed = new OrderStatus();
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);

        when(orderRepository.findLockedByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(confirmed);
        when(orderStatusService.canMoveTo(OrderStatusCd.CONFIRMED, OrderStatusCd.IN_PRODUCTION)).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> service.updateOrderStatus(
                "order-guid-10", new UpdateOrderStatusRequest(OrderStatusCd.IN_PRODUCTION, null, null)));
        verify(orderStatusService, never()).move(order, OrderStatusCd.IN_PRODUCTION);
    }

    @Test
    void updateOrderStatusThrowsConflictOnInvalidStateMachineTransition() {
        ServiceContext.getContext().setUser(makerUser);
        OrderStatus created = new OrderStatus();
        created.setStatusCd(OrderStatusCd.CREATED);
        order.setVendorId(50L);

        when(orderRepository.findLockedByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(created);
        when(orderStatusService.canMoveTo(OrderStatusCd.CREATED, OrderStatusCd.COMPLETED)).thenReturn(false);

        assertThrows(ConflictException.class, () ->
                service.updateOrderStatus("order-guid-10", new UpdateOrderStatusRequest(OrderStatusCd.COMPLETED, null, null))
        );
    }

    @Test
    void listOrderFilesReturnsMultiPlateFilesForAuthorizedMaker() {
        order.setVendorId(null);
        ServiceContext.getContext().setUser(makerUser);

        OrderStatus quotingStatus = new OrderStatus();
        quotingStatus.setStatusCd(OrderStatusCd.QUOTING);

        Company company = new Company();
        company.setId(50L);
        company.setGuid("company-guid-50");

        FileAsset asset1 = new FileAsset();
        asset1.setId(101L);
        asset1.setGuid("file-guid-1");
        asset1.setName("base.stl");

        FileAsset asset2 = new FileAsset();
        asset2.setId(102L);
        asset2.setGuid("file-guid-2");
        asset2.setName("tampa.stl");

        OrderFileAsset plate1 = new OrderFileAsset();
        plate1.setOrderId(10L);
        plate1.setFileAssetId(101L);
        plate1.setPlateNumber(1);
        plate1.setPlateName("Mesa 1");
        plate1.setMaterial("PLA");
        plate1.setColorHex("#8b5cf6");
        plate1.setInfillPercent(20);
        plate1.setSupports(true);
        plate1.setQuantity(2);
        plate1.setWallCount(3);
        plate1.setInfillPattern("gyroid");
        plate1.setAdhesionType("brim");
        plate1.setSupportType("tree");
        plate1.setFinish("sanded");
        plate1.setTolerance("tight");
        plate1.setPlateNotes("Sem marcas na face frontal");
        plate1.setColorsJson("[\"#8b5cf6\",\"#f59e0b\"]");
        plate1.setEstimatedWeightGrams(new BigDecimal("48.50"));
        plate1.setEstimatedPrintTimeMinutes(135);
        plate1.setEstimatedPriceBrl(new BigDecimal("42.90"));

        OrderFileAsset plate2 = new OrderFileAsset();
        plate2.setOrderId(10L);
        plate2.setFileAssetId(102L);
        plate2.setPlateNumber(2);
        plate2.setPlateName("Mesa 2");
        plate2.setMaterial("PETG");
        plate2.setColorHex("#10b981");
        plate2.setInfillPercent(40);
        plate2.setSupports(false);
        plate2.setScaleX(1.5);
        plate2.setScaleY(1.5);
        plate2.setScaleZ(1.5);

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quotingStatus);
        when(companyRepository.findById(50L)).thenReturn(Optional.of(company));
        when(chatRepository.existsByOrderGuidAndCompanyGuid("order-guid-10", "company-guid-50")).thenReturn(true);
        when(orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(plate1, plate2));
        when(fileAssetRepository.findAllByIdIn(Set.of(101L, 102L))).thenReturn(List.of(asset1, asset2));

        List<OrderFileResponse> files = service.listOrderFiles("order-guid-10");

        assertEquals(2, files.size());
        assertEquals("file-guid-1", files.get(0).fileGuid());
        assertEquals("Mesa 1", files.get(0).plateName());
        assertEquals("PLA", files.get(0).material());
        assertEquals(2, files.get(0).quantity());
        assertEquals(3, files.get(0).wallCount());
        assertEquals("gyroid", files.get(0).infillPattern());
        assertEquals("sanded", files.get(0).finish());
        assertEquals("Sem marcas na face frontal", files.get(0).notes());
        assertEquals(List.of("#8b5cf6", "#f59e0b"), files.get(0).colors());
        assertEquals(new BigDecimal("48.50"), files.get(0).estimatedWeightGrams());
        assertEquals(135, files.get(0).estimatedPrintTimeMinutes());
        assertEquals(new BigDecimal("42.90"), files.get(0).estimatedPriceBrl());
        assertEquals("file-guid-2", files.get(1).fileGuid());
        assertEquals("Mesa 2", files.get(1).plateName());
        assertEquals(1.5, files.get(1).scaleX());
    }

    @Test
    void listOrderFilesFallsBackToLegacyOrderFileAssetId() {
        ServiceContext.getContext().setUser(buyer);
        order.setFileAssetId(77L);

        FileAsset legacyAsset = new FileAsset();
        legacyAsset.setId(77L);
        legacyAsset.setGuid("legacy-guid");
        legacyAsset.setName("peca_legada.stl");

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(fileAssetRepository.findById(77L)).thenReturn(Optional.of(legacyAsset));

        List<OrderFileResponse> files = service.listOrderFiles("order-guid-10");

        assertEquals(1, files.size());
        assertEquals("legacy-guid", files.getFirst().fileGuid());
        assertEquals("peca_legada.stl", files.getFirst().fileName());
        assertEquals(1, files.getFirst().plateNumber());
        assertEquals(1.0, files.getFirst().scaleX());
    }

    @Test
    void quotingMakerCanReadMetadataButCannotDownloadOriginal() {
        ServiceContext.getContext().setUser(makerUser);
        order.setVendorId(null);
        OrderStatus quoting = new OrderStatus();
        quoting.setStatusCd(OrderStatusCd.QUOTING);
        Company company = new Company();
        company.setGuid("company-guid");
        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quoting);
        when(companyRepository.findById(makerUser.getCompanyId())).thenReturn(Optional.of(company));
        when(chatRepository.existsByOrderGuidAndCompanyGuid("order-guid-10", "company-guid")).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> service.downloadOrderFile("order-guid-10", "file-guid"));
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void downloadOrderFileReturnsBytesWhenAssetBelongsToOrder() {
        ServiceContext.getContext().setUser(makerUser);
        Company activeCompany = new Company();
        activeCompany.setStatus(StatusCd.ACTIVE);
        when(companyRepository.findById(makerUser.getCompanyId())).thenReturn(Optional.of(activeCompany));

        FileAsset asset = new FileAsset();
        asset.setId(101L);
        asset.setGuid("file-guid-1");
        asset.setName("peca.stl");

        OrderFileAsset plate = new OrderFileAsset();
        plate.setOrderId(10L);
        plate.setFileAssetId(101L);

        byte[] payload = "solid demo\nendsolid demo\n".getBytes();

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(fileAssetRepository.findByGuid("file-guid-1")).thenReturn(Optional.of(asset));
        when(orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(plate));
        when(objectStorageService.downloadPrivateOrderFile(asset)).thenReturn(payload);

        OrderManagementService.OrderFileContent content = service.downloadOrderFile("order-guid-10", "file-guid-1");

        assertEquals("peca.stl", content.fileName());
        assertEquals("model/stl", content.contentType());
        assertArrayEquals(payload, content.bytes());
    }

    @Test
    void downloadOrderFileRejectsSuspendedSellerCompany() {
        ServiceContext.getContext().setUser(makerUser);
        Company suspended = new Company();
        suspended.setStatus(StatusCd.SUSPENDED);
        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(companyRepository.findById(makerUser.getCompanyId())).thenReturn(Optional.of(suspended));

        assertThrows(ForbiddenException.class, () -> service.downloadOrderFile("order-guid-10", "file-guid"));
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void downloadOrderFileRejectsUnrelatedFileGuidWithNotFound() {
        ServiceContext.getContext().setUser(buyer);
        order.setFileAssetId(10L);

        FileAsset unrelated = new FileAsset();
        unrelated.setId(999L);
        unrelated.setGuid("other-file-guid");
        unrelated.setName("secreto.stl");

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(fileAssetRepository.findByGuid("other-file-guid")).thenReturn(Optional.of(unrelated));
        when(orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of());

        assertThrows(NotFoundException.class, () -> service.downloadOrderFile("order-guid-10", "other-file-guid"));
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void listOrderFilesRejectsOutsiderWithForbidden() {
        ServiceContext.getContext().setUser(outsider);
        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> service.listOrderFiles("order-guid-10"));
    }
}
