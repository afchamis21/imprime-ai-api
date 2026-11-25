package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.StartOrderRequest;
import org.imprime.ai.api.http.response.StartOrderResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private AddressService addressService;
    @Mock
    private CompanyService companyService;
    @Mock
    private FileAssetService fileAssetService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderFileAssetRepository orderFileAssetRepository;
    @Mock
    private OrderStatusService orderStatusService;

    private OrderService orderService;
    private User buyer;
    private Address address;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                addressService,
                companyService,
                fileAssetService,
                orderRepository,
                orderFileAssetRepository,
                orderStatusService,
                new JsonCodec()
        );

        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-guid");
        ServiceContext.getContext().setUser(buyer);

        address = new Address();
        address.setId(10L);
        address.setGuid("addr-guid-10");
        address.setOwnerType(EntityType.USER);
        address.setOwnerId(1L);
        address.setStatus(StatusCd.ACTIVE);
        address.setZipCode("01310-100");
        address.setCity("São Paulo");
        address.setState("SP");
        address.setCountry("Brasil");
        address.setAddressLine1("Av. Paulista, 100");
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void startWithMultiplePlatesUploadsAllFilesAndPersistsOrderFileAssets() {
        MockMultipartFile file1 = new MockMultipartFile("file", "mesa1.stl", "model/stl", "stl-1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "mesa2.stl", "model/stl", "stl-2".getBytes());

        FileAsset asset1 = new FileAsset();
        asset1.setId(501L);
        asset1.setGuid("asset-guid-1");
        asset1.setName("mesa1.stl");

        FileAsset asset2 = new FileAsset();
        asset2.setId(502L);
        asset2.setGuid("asset-guid-2");
        asset2.setName("mesa2.stl");

        String metadataJson = """
                [
                  {"plateNumber": 1, "plateName": "Mesa Principal", "material": "PLA", "colorHex": "#8b5cf6", "colors": ["#8b5cf6"], "infillPercent": 20, "supports": true, "scaleX": 1.0, "scaleY": 1.0, "scaleZ": 1.0, "isMultiColor": false, "dimensionXMm": 120.5, "dimensionYMm": 80.0, "dimensionZMm": 45.0, "colorCount": 1, "layerHeightMm": 0.20, "urgency": "STANDARD", "quantity": 2, "wallCount": 3, "infillPattern": "gyroid", "adhesionType": "brim", "supportType": "tree", "finish": "sanded", "tolerance": "tight", "notes": "Sem rebarbas", "estimatedWeightGrams": 48.50, "estimatedPrintTimeMinutes": 135, "estimatedPriceBrl": 42.90},
                  {"plateNumber": 2, "plateName": "Mesa Tampa", "material": "PETG", "colorHex": "#10b981", "colors": ["#10b981", "#ffffff"], "infillPercent": 45, "supports": false, "scaleX": 1.25, "scaleY": 1.25, "scaleZ": 1.25, "isMultiColor": true, "colorCount": 4, "dimensionXMm": 200.0, "dimensionYMm": 150.0, "dimensionZMm": 90.0, "paintingInstructions": "Tampa verde"}
                ]
                """;

        when(addressService.findByGuid("addr-guid-10")).thenReturn(Optional.of(address));
        when(fileAssetService.upload(file1, FileAsset.AssetType.MODEL)).thenReturn(asset1);
        when(fileAssetService.upload(file2, FileAsset.AssetType.MODEL)).thenReturn(asset2);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(900L);
            o.setGuid("order-guid-900");
            return o;
        });
        when(orderFileAssetRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(companyService.findCompaniesForOrder(eq(address), anyList(), eq(100))).thenReturn(List.of());

        StartOrderResponse response = orderService.start(
                List.of(file1, file2),
                metadataJson,
                new StartOrderRequest("addr-guid-10")
        );

        assertEquals("order-guid-900", response.orderGuid());
        verify(companyService).findCompaniesForOrder(eq(address), anyList(), eq(100));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(501L, orderCaptor.getValue().getFileAssetId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderFileAsset>> platesCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderFileAssetRepository).saveAll(platesCaptor.capture());
        List<OrderFileAsset> savedPlates = platesCaptor.getValue();
        assertEquals(2, savedPlates.size());

        assertEquals(501L, savedPlates.get(0).getFileAssetId());
        assertEquals("Mesa Principal", savedPlates.get(0).getPlateName());
        assertEquals("PLA", savedPlates.get(0).getMaterial());
        assertEquals(20, savedPlates.get(0).getInfillPercent());
        assertEquals(new BigDecimal("120.5"), savedPlates.get(0).getDimensionXMm());
        assertEquals(1, savedPlates.get(0).getColorCount());
        assertEquals("STANDARD", savedPlates.get(0).getUrgency());
        assertEquals(2, savedPlates.get(0).getQuantity());
        assertEquals(3, savedPlates.get(0).getWallCount());
        assertEquals("gyroid", savedPlates.get(0).getInfillPattern());
        assertEquals("brim", savedPlates.get(0).getAdhesionType());
        assertEquals("tree", savedPlates.get(0).getSupportType());
        assertEquals("sanded", savedPlates.get(0).getFinish());
        assertEquals("tight", savedPlates.get(0).getTolerance());
        assertEquals("Sem rebarbas", savedPlates.get(0).getPlateNotes());
        assertEquals("[\"#8b5cf6\"]", savedPlates.get(0).getColorsJson());
        assertEquals(new BigDecimal("48.50"), savedPlates.get(0).getEstimatedWeightGrams());
        assertEquals(135, savedPlates.get(0).getEstimatedPrintTimeMinutes());
        assertEquals(new BigDecimal("42.90"), savedPlates.get(0).getEstimatedPriceBrl());

        assertEquals(502L, savedPlates.get(1).getFileAssetId());
        assertEquals("Mesa Tampa", savedPlates.get(1).getPlateName());
        assertEquals("PETG", savedPlates.get(1).getMaterial());
        assertEquals(1.25, savedPlates.get(1).getScaleX());
        assertTrue(savedPlates.get(1).getIsMultiColor());
        assertEquals(4, savedPlates.get(1).getColorCount());
        assertEquals(new BigDecimal("200.0"), savedPlates.get(1).getDimensionXMm());
        assertEquals("Tampa verde", savedPlates.get(1).getPaintingInstructions());
        assertEquals(1, savedPlates.get(1).getQuantity());
        assertEquals("[\"#10b981\",\"#ffffff\"]", savedPlates.get(1).getColorsJson());

        verify(orderStatusService).move(any(Order.class), eq(OrderStatusCd.CREATED));
        verify(orderStatusService).move(any(Order.class), eq(OrderStatusCd.QUOTING));
    }

    @Test
    void startRollsBackPreviousUploadsWhenSubsequentUploadFails() {
        MockMultipartFile file1 = new MockMultipartFile("file", "mesa1.stl", "model/stl", "stl-1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "bad.txt", "text/plain", "nope".getBytes());

        FileAsset asset1 = new FileAsset();
        asset1.setId(501L);
        asset1.setGuid("asset-guid-1");

        when(addressService.findByGuid("addr-guid-10")).thenReturn(Optional.of(address));
        when(fileAssetService.upload(file1, FileAsset.AssetType.MODEL)).thenReturn(asset1);
        when(fileAssetService.upload(file2, FileAsset.AssetType.MODEL))
                .thenThrow(new BadRequestException(MessageCd.FILE_UNSUPPORTED_EXTENSION));

        assertThrows(BadRequestException.class, () ->
                orderService.start(List.of(file1, file2), null, new StartOrderRequest("addr-guid-10"))
        );

        verify(fileAssetService).deleteForever(asset1);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderFileAssetRepository);
    }

    @Test
    void cloneOrderForNewQuoteCopiesOrderFileAssets() {
        Order template = new Order();
        template.setId(10L);
        template.setToAddressId(5L);
        template.setFileAssetId(501L);

        OrderFileAsset src1 = new OrderFileAsset();
        src1.setOrderId(10L);
        src1.setFileAssetId(501L);
        src1.setPlateNumber(1);
        src1.setPlateName("Mesa 1");
        src1.setMaterial("PLA");
        src1.setScaleX(1.0);
        src1.setScaleY(1.0);
        src1.setScaleZ(1.0);
        src1.setQuantity(3);
        src1.setWallCount(4);
        src1.setInfillPattern("grid");
        src1.setAdhesionType("brim");
        src1.setSupportType("tree");
        src1.setFinish("sanded");
        src1.setTolerance("tight");
        src1.setPlateNotes("Acabamento fino");
        src1.setColorsJson("[\"#8b5cf6\"]");
        src1.setEstimatedWeightGrams(new BigDecimal("64.20"));
        src1.setEstimatedPrintTimeMinutes(180);
        src1.setEstimatedPriceBrl(new BigDecimal("58.00"));

        OrderFileAsset src2 = new OrderFileAsset();
        src2.setOrderId(10L);
        src2.setFileAssetId(502L);
        src2.setPlateNumber(2);
        src2.setPlateName("Mesa 2");
        src2.setMaterial("ABS");
        src2.setScaleX(1.5);
        src2.setScaleY(1.5);
        src2.setScaleZ(1.5);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(99L);
            return o;
        });
        when(orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(src1, src2));

        Order cloned = orderService.cloneOrderForNewQuote(template, buyer);

        assertEquals(99L, cloned.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderFileAsset>> clonedCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderFileAssetRepository).saveAll(clonedCaptor.capture());
        List<OrderFileAsset> copied = clonedCaptor.getValue();
        assertEquals(2, copied.size());
        assertEquals(99L, copied.get(0).getOrderId());
        assertEquals(501L, copied.get(0).getFileAssetId());
        assertEquals(3, copied.get(0).getQuantity());
        assertEquals(4, copied.get(0).getWallCount());
        assertEquals("sanded", copied.get(0).getFinish());
        assertEquals("Acabamento fino", copied.get(0).getPlateNotes());
        assertEquals(new BigDecimal("64.20"), copied.get(0).getEstimatedWeightGrams());
        assertEquals(180, copied.get(0).getEstimatedPrintTimeMinutes());
        assertEquals(99L, copied.get(1).getOrderId());
        assertEquals(502L, copied.get(1).getFileAssetId());
        assertEquals("ABS", copied.get(1).getMaterial());
        assertEquals(1.5, copied.get(1).getScaleX());
    }
}
