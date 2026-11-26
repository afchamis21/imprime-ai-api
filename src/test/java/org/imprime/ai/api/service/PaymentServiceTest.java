package org.imprime.ai.api.service;

import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.payment.CheckoutItemRequest;
import org.imprime.ai.api.http.request.payment.CompleteCheckoutRequest;
import org.imprime.ai.api.http.response.CompleteCheckoutResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.common.OrderItemResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.PurchasedModel;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.PurchasedModelRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private StripeConfigProperties stripeConfig;
    @Mock
    private AddressService addressService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private PurchasedModelRepository purchasedModelRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private PaymentService paymentService;

    private User buyer;
    private Address buyerAddress;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(10L);
        buyer.setGuid("buyer-guid-10");
        buyer.setFirstName("Gabriel");
        buyer.setLastName("Amorim");
        buyer.setEmail("gabriel@exemplo.com");

        buyerAddress = new Address();
        buyerAddress.setId(100L);
        buyerAddress.setGuid("addr-guid-100");
        buyerAddress.setOwnerType(EntityType.USER);
        buyerAddress.setOwnerId(10L);
        buyerAddress.setStatus(StatusCd.ACTIVE);
        buyerAddress.setZipCode("01310-100");
        buyerAddress.setAddressLine1("Av. Paulista, 1000");
        buyerAddress.setNeighborhood("Bela Vista");
        buyerAddress.setCity("São Paulo");
        buyerAddress.setState("SP");
        buyerAddress.setCountry("Brasil");

        ServiceContext.getContext().setUser(buyer);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void completeCartCheckoutPersistsProductOrderAndStartsProduction() {
        Product product = new Product();
        product.setId(501L);
        product.setGuid("prod-guid-501");
        product.setCompanyId(20L);
        product.setName("Suporte Ergonômico");
        product.setMaterial(MaterialType.PETG);
        product.setPrice(new BigDecimal("60.00"));
        product.setSalesCount(3);

        Address companyAddr = new Address();
        companyAddr.setId(200L);

        when(orderRepository.findAllByCheckoutSessionIdAndBuyerIdAndStatusOrderByIdAsc(
                "cs_test_sim_prod", 10L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(addressService.findByGuid("addr-guid-100")).thenReturn(Optional.of(buyerAddress));
        when(productRepository.findByGuidAndStatus("prod-guid-501", StatusCd.ACTIVE))
                .thenReturn(Optional.of(product));
        when(addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.COMPANY, 20L, StatusCd.ACTIVE))
                .thenReturn(List.of(companyAddr));
        when(orderViewAssembler.formatOrderCode(any())).thenReturn("PED-ABC123");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(901L);
            return o;
        });
        when(orderViewAssembler.toSummaryResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return new OrderSummaryResponse(
                    o.getGuid(), o.getOrderCode(), OrderStatusCd.IN_PRODUCTION,
                    new BigDecimal("120.00"), o.getShippingCost(), o.getOrderTotal(),
                    null, o.getNotes(),
                    new MakerBriefResponse("comp-20", "studio", "Studio 3D", null, new BigDecimal("5.0"), 1, true),
                    null, List.of(), Instant.now(), Instant.now()
            );
        });

        CompleteCheckoutRequest req = new CompleteCheckoutRequest(
                "cs_test_sim_prod",
                List.of(new CheckoutItemRequest(
                        "prod-guid-501", "PRODUCT", "prod-guid-501", null, "comp-20",
                        "Preto", "Suporte Ergonômico", "Studio 3D", "PETG", null,
                        new BigDecimal("60.00"), 2
                )),
                "addr-guid-100", null,
                "Gabriel", "gabriel@exemplo.com", null, "Entregar na portaria",
                "brl", new BigDecimal("120.00"), new BigDecimal("18.90"), new BigDecimal("138.90")
        );

        CompleteCheckoutResponse response = paymentService.completeCartCheckout(req);

        assertFalse(response.alreadyProcessed());
        assertEquals(1, response.orders().size());
        assertEquals(new BigDecimal("138.90"), response.totalAmount());
        assertEquals(5, product.getSalesCount());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order persisted = orderCaptor.getValue();
        assertEquals(10L, persisted.getBuyerId());
        assertEquals(20L, persisted.getVendorId());
        assertEquals(200L, persisted.getFromAddressId());
        assertEquals(100L, persisted.getToAddressId());
        assertEquals(new BigDecimal("18.90"), persisted.getShippingCost());
        assertEquals(new BigDecimal("138.90"), persisted.getOrderTotal());
        assertEquals("cs_test_sim_prod", persisted.getCheckoutSessionId());

        verify(orderItemRepository).save(any(OrderItem.class));
        verify(productRepository).save(product);
        verify(orderStatusService).move(any(Order.class), eq(OrderStatusCd.CREATED));
        verify(orderStatusService).move(any(Order.class), eq(OrderStatusCd.CONFIRMED));
        verify(orderStatusService, never()).move(any(Order.class), eq(OrderStatusCd.IN_PRODUCTION));
    }

    @Test
    void completeCartCheckoutPersistsDigitalModelOrderAndPurchasedModelWithZeroShipping() {
        Model3D model = new Model3D();
        model.setId(77L);
        model.setGuid("model-guid-77");
        model.setAuthorUserId(30L);
        model.setName("Dragão Articulado");
        model.setRecommendedMaterial(MaterialType.PLA);
        model.setLicense(LicenseType.PERSONAL);
        model.setPrice(new BigDecimal("34.90"));

        User author = new User();
        author.setId(30L);
        author.setCompanyId(40L);

        when(orderRepository.findAllByCheckoutSessionIdAndBuyerIdAndStatusOrderByIdAsc(
                "cs_test_sim_model", 10L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(addressService.findByGuid("addr-guid-100")).thenReturn(Optional.of(buyerAddress));
        when(model3DRepository.findByGuidAndStatus("model-guid-77", StatusCd.ACTIVE))
                .thenReturn(Optional.of(model));
        when(userRepository.findById(30L)).thenReturn(Optional.of(author));
        when(purchasedModelRepository.existsByBuyerUserIdAndModel3dIdAndStatus(10L, 77L, StatusCd.ACTIVE))
                .thenReturn(false);
        when(orderViewAssembler.formatOrderCode(any())).thenReturn("PED-MOD077");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(902L);
            return o;
        });
        when(orderViewAssembler.toSummaryResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return new OrderSummaryResponse(
                    o.getGuid(), o.getOrderCode(), OrderStatusCd.COMPLETED,
                    new BigDecimal("34.90"), o.getShippingCost(), o.getOrderTotal(),
                    null, null, null, null, List.of(), Instant.now(), Instant.now()
            );
        });

        CompleteCheckoutRequest req = new CompleteCheckoutRequest(
                "cs_test_sim_model",
                List.of(new CheckoutItemRequest(
                        "model-model-guid-77", "MODEL_3D", null, "model-guid-77", null,
                        null, "Dragão Articulado", "DracoForge", "PLA", null,
                        new BigDecimal("34.90"), 1
                )),
                "addr-guid-100", null,
                "Gabriel", "gabriel@exemplo.com", null, null,
                "brl", new BigDecimal("34.90"), new BigDecimal("18.90"), new BigDecimal("34.90")
        );

        CompleteCheckoutResponse response = paymentService.completeCartCheckout(req);

        assertFalse(response.alreadyProcessed());
        assertEquals(1, response.orders().size());
        assertEquals(new BigDecimal("34.90"), response.totalAmount());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals(40L, savedOrder.getVendorId());
        assertEquals(new BigDecimal("0.00"), savedOrder.getShippingCost());
        assertEquals(new BigDecimal("34.90"), savedOrder.getOrderTotal());

        ArgumentCaptor<PurchasedModel> purchasedCaptor = ArgumentCaptor.forClass(PurchasedModel.class);
        verify(purchasedModelRepository).save(purchasedCaptor.capture());
        assertEquals(10L, purchasedCaptor.getValue().getBuyerUserId());
        assertEquals(77L, purchasedCaptor.getValue().getModel3dId());
        assertEquals(902L, purchasedCaptor.getValue().getOrderId());
        assertEquals(LicenseType.PERSONAL, purchasedCaptor.getValue().getLicense());

        verify(orderStatusService).move(any(Order.class), eq(OrderStatusCd.CREATED));
        verify(orderStatusService).move(any(Order.class), eq(OrderStatusCd.CONFIRMED));
        verify(orderStatusService).move(any(Order.class), eq(OrderStatusCd.COMPLETED));
    }

    @Test
    void completeCartCheckoutSplitsMultiVendorCartIntoSeparateOrdersAndAllocatesShipping() {
        Product p1 = new Product();
        p1.setId(1L);
        p1.setGuid("prod-1");
        p1.setCompanyId(101L);
        p1.setName("Peça A");
        p1.setMaterial(MaterialType.PLA);
        p1.setPrice(new BigDecimal("50.00"));

        Product p2 = new Product();
        p2.setId(2L);
        p2.setGuid("prod-2");
        p2.setCompanyId(202L);
        p2.setName("Peça B");
        p2.setMaterial(MaterialType.PETG);
        p2.setPrice(new BigDecimal("80.00"));

        when(orderRepository.findAllByCheckoutSessionIdAndBuyerIdAndStatusOrderByIdAsc(
                "cs_test_sim_multi", 10L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(addressService.findByGuid("addr-guid-100")).thenReturn(Optional.of(buyerAddress));
        when(productRepository.findByGuidAndStatus("prod-1", StatusCd.ACTIVE)).thenReturn(Optional.of(p1));
        when(productRepository.findByGuidAndStatus("prod-2", StatusCd.ACTIVE)).thenReturn(Optional.of(p2));

        AtomicLong idSeq = new AtomicLong(1000L);
        when(orderViewAssembler.formatOrderCode(any())).thenReturn("PED-MULTI");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(idSeq.incrementAndGet());
            return o;
        });
        when(orderViewAssembler.toSummaryResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return new OrderSummaryResponse(
                    o.getGuid(), o.getOrderCode(), OrderStatusCd.IN_PRODUCTION,
                    o.getOrderTotal().subtract(o.getShippingCost()), o.getShippingCost(), o.getOrderTotal(),
                    null, null, null, null, List.of(), Instant.now(), Instant.now()
            );
        });

        CompleteCheckoutRequest req = new CompleteCheckoutRequest(
                "cs_test_sim_multi",
                List.of(
                        new CheckoutItemRequest(
                                "prod-1", "PRODUCT", "prod-1", null, null,
                                null, "Peça A", "Maker A", "PLA", null,
                                new BigDecimal("50.00"), 1
                        ),
                        new CheckoutItemRequest(
                                "prod-2", "PRODUCT", "prod-2", null, null,
                                null, "Peça B", "Maker B", "PETG", null,
                                new BigDecimal("80.00"), 1
                        )
                ),
                "addr-guid-100", null,
                "Gabriel", "gabriel@exemplo.com", null, null,
                "brl", new BigDecimal("130.00"), new BigDecimal("10.01"), new BigDecimal("140.01")
        );

        CompleteCheckoutResponse response = paymentService.completeCartCheckout(req);

        assertEquals(2, response.orders().size());
        assertEquals(new BigDecimal("140.01"), response.totalAmount());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(orderCaptor.capture());
        List<Order> savedOrders = orderCaptor.getAllValues();

        assertEquals(101L, savedOrders.get(0).getVendorId());
        assertEquals(new BigDecimal("5.01"), savedOrders.get(0).getShippingCost());
        assertEquals(new BigDecimal("55.01"), savedOrders.get(0).getOrderTotal());

        assertEquals(202L, savedOrders.get(1).getVendorId());
        assertEquals(new BigDecimal("5.00"), savedOrders.get(1).getShippingCost());
        assertEquals(new BigDecimal("85.00"), savedOrders.get(1).getOrderTotal());
    }

    @Test
    void completeCartCheckoutReturnsExistingOrdersIdempotentlyForSameSession() {
        Order existing = new Order();
        existing.setId(555L);
        existing.setGuid("existing-order-guid");
        existing.setOrderCode("PED-EXIST1");
        existing.setOrderTotal(new BigDecimal("99.90"));

        OrderSummaryResponse existingSummary = new OrderSummaryResponse(
                "existing-order-guid", "PED-EXIST1", OrderStatusCd.IN_PRODUCTION,
                new BigDecimal("81.00"), new BigDecimal("18.90"), new BigDecimal("99.90"),
                null, null, null, null,
                List.of(new OrderItemResponse("prod-1", "Item", MaterialType.PLA, null, 1, new BigDecimal("81.00"), new BigDecimal("81.00"))),
                Instant.now(), Instant.now()
        );

        when(orderRepository.findAllByCheckoutSessionIdAndBuyerIdAndStatusOrderByIdAsc(
                "cs_live_replay_1", 10L, StatusCd.ACTIVE)).thenReturn(List.of(existing));
        when(orderViewAssembler.toSummaryResponse(existing)).thenReturn(existingSummary);

        CompleteCheckoutRequest req = new CompleteCheckoutRequest(
                "cs_live_replay_1",
                List.of(new CheckoutItemRequest(
                        "prod-1", "PRODUCT", "prod-1", null, null,
                        null, "Item", "Maker", "PLA", null, new BigDecimal("81.00"), 1
                )),
                "addr-guid-100", null,
                "Gabriel", "gabriel@exemplo.com", null, null,
                "brl", new BigDecimal("81.00"), new BigDecimal("18.90"), new BigDecimal("99.90")
        );

        CompleteCheckoutResponse response = paymentService.completeCartCheckout(req);

        assertTrue(response.alreadyProcessed());
        assertEquals(1, response.orders().size());
        assertEquals("PED-EXIST1", response.orders().getFirst().orderCode());
        assertEquals(new BigDecimal("99.90"), response.totalAmount());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderItemRepository, purchasedModelRepository, orderStatusService);
    }
}
