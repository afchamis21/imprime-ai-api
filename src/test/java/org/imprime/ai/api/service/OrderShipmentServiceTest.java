package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.payment.CalculateShippingQuotesRequest;
import org.imprime.ai.api.http.request.payment.CheckoutItemRequest;
import org.imprime.ai.api.http.response.order.OrderShipmentResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderShipment;
import org.imprime.ai.api.model.OrderShippingQuote;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.OrderShipmentStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderShipmentRepository;
import org.imprime.ai.api.repo.db.OrderShippingQuoteRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.OrderManagementService.OrderFileContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderShipmentServiceTest {

    @Mock
    private MelhorEnvioClient melhorEnvioClient;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderShipmentRepository shipmentRepository;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private OrderShippingQuoteRepository quoteRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    private ObjectMapper objectMapper;
    private OrderShipmentService orderShipmentService;
    private ShippingQuoteService shippingQuoteService;

    private User makerUser;
    private User buyerUser;
    private Order order;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        orderShipmentService = new OrderShipmentService(
                melhorEnvioClient,
                orderRepository,
                shipmentRepository,
                orderStatusService,
                addressRepository,
                objectMapper
        );
        shippingQuoteService = new ShippingQuoteService(
                melhorEnvioClient,
                quoteRepository,
                addressRepository,
                companyRepository,
                productRepository,
                userRepository,
                objectMapper
        );

        makerUser = new User();
        makerUser.setId(20L);
        makerUser.setGuid("maker-user-20");
        makerUser.setCompanyId(200L);
        makerUser.setEmail("maker@imprimeai.shop");
        makerUser.setFirstName("Loja");
        makerUser.setLastName("Teste");
        makerUser.setDocument("12345678909");
        makerUser.setPhoneCountry("55");
        makerUser.setPhoneNumber("5531989877783");

        buyerUser = new User();
        buyerUser.setId(10L);
        buyerUser.setGuid("buyer-user-10");
        buyerUser.setEmail("comprador@imprimeai.shop");
        buyerUser.setFirstName("Gabriel");
        buyerUser.setLastName("Amorim");
        buyerUser.setDocument("98765432100");
        buyerUser.setPhoneCountry("+55");
        buyerUser.setPhoneNumber("31912345678");

        order = new Order();
        order.setId(900L);
        order.setGuid("order-guid-900");
        order.setOrderCode("PED-900");
        order.setBuyerId(10L);
        order.setVendorId(200L);
        order.setShippingServiceId(4);
        order.setShippingServiceName(".Package");
        order.setCarrierName("Jadlog");
        order.setShippingQuotePrice(new BigDecimal("18.90"));
        order.setShippingQuoteSnapshotJson("""
                {
                  "quote": { "company": { "name": "Jadlog" } },
                  "cart": {
                    "from": {
                      "name": "Lojinha Teste",
                      "phone": "5531988887777",
                      "company_document": "12345678909",
                      "state_register": "ISENTO",
                      "postal_code": "30140071"
                    },
                    "to": {
                      "name": "Gabriel Amorim",
                      "phone": "5531999991111",
                      "document": "98765432100",
                      "state_register": "ISENTO",
                      "postal_code": "32315100"
                    },
                    "products": [
                      { "name": "Chaveiro", "quantity": 1, "unitary_value": 65.00 }
                    ],
                    "volumes": [
                      { "width": 10, "height": 10, "length": 10, "weight": 0.500, "insurance": 65.00 }
                    ],
                    "options": {
                      "platform": "Imprime.AI",
                      "reminder": "Pedido Imprime.AI",
                      "receipt": false,
                      "own_hand": false
                    }
                  }
                }
                """);

        ServiceContext.getContext().setUser(makerUser);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateNormalizesLegacyCartSnapshotAndCreatesShipment() {
        OrderStatus readyStatus = new OrderStatus();
        readyStatus.setStatusCd(OrderStatusCd.READY_TO_SHIP);

        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(readyStatus);
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        JsonNode cartResp = objectMapper.readTree("{\"id\":\"me-cart-1\",\"price\":\"18.90\"}");
        JsonNode cartDetail = objectMapper.readTree("{\"id\":\"me-cart-1\",\"price\":\"18.90\",\"status\":\"pending\"}");
        JsonNode orderDetail = objectMapper.readTree("{\"id\":\"me-cart-1\",\"status\":\"released\",\"generated_at\":null}");
        when(melhorEnvioClient.get("/api/v2/me/shipment/agencies?company=2")).thenReturn(objectMapper.readTree("[]"));
        when(melhorEnvioClient.post(eq("/api/v2/me/cart"), any())).thenReturn(cartResp);
        when(melhorEnvioClient.get("/api/v2/me/cart/me-cart-1")).thenReturn(cartDetail);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/checkout"), any()))
                .thenReturn(objectMapper.readTree("{\"purchase\":{\"status\":\"paid\"}}"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/generate"), any()))
                .thenReturn(objectMapper.readTree("{}"));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-cart-1")).thenReturn(orderDetail);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any()))
                .thenReturn(objectMapper.readTree("{}"));

        OrderShipmentResponse response = orderShipmentService.generate("order-guid-900");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(melhorEnvioClient).post(eq("/api/v2/me/cart"), payloadCaptor.capture());

        Map<String, Object> sent = payloadCaptor.getValue();
        assertEquals(4, sent.get("service"));

        Map<String, Object> options = (Map<String, Object>) sent.get("options");
        assertEquals(Boolean.TRUE, options.get("non_commercial"));
        assertEquals(Boolean.FALSE, options.get("reverse"));
        assertEquals(0, new BigDecimal("65.00").compareTo(new BigDecimal(String.valueOf(options.get("insurance_value")))));

        List<Map<String, Object>> volumes = (List<Map<String, Object>>) sent.get("volumes");
        assertFalse(volumes.get(0).containsKey("insurance"));
        assertEquals(11, volumes.get(0).get("width"));
        assertEquals(10, volumes.get(0).get("height"));
        assertEquals(16, volumes.get(0).get("length"));

        Map<String, Object> from = (Map<String, Object>) sent.get("from");
        assertEquals("31988887777", from.get("phone"));
        assertEquals("12345678909", from.get("document"));
        assertEquals("Centro", from.get("district"));
        assertFalse(from.containsKey("company_document"));
        assertFalse(from.containsKey("state_register"));

        Map<String, Object> to = (Map<String, Object>) sent.get("to");
        assertEquals("31999991111", to.get("phone"));
        assertEquals("Centro", to.get("district"));

        assertNull(response.packages().get(0).lastError());
        assertEquals("GENERATING", response.packages().get(0).status());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateRepairsCorreiosSnapshotWithBlankDistrictBlankComplementAndSmallDimensions() {
        order.setShippingServiceId(2);
        order.setShippingServiceName("SEDEX");
        order.setCarrierName("Correios");
        order.setFromAddressId(60L);
        order.setToAddressId(50L);
        order.setShippingQuoteSnapshotJson("""
                {
                  "quote": { "id": 2, "name": "SEDEX", "company": { "id": 1, "name": "Correios" } },
                  "cart": {
                    "to": {
                      "city": "Belo Horizonte",
                      "name": "Gabriel Amorim",
                      "email": "gabriel._amorim@hotmail.com",
                      "phone": "31989877783",
                      "number": "397",
                      "address": "Rua Padre João Crisóstomo",
                      "district": "Coração Eucarístico",
                      "document": "11855082608",
                      "complement": "",
                      "country_id": "BR",
                      "state_abbr": "MG",
                      "postal_code": "30535510"
                    },
                    "from": {
                      "city": "Contagem",
                      "name": "Lojinha Teste",
                      "email": "teste@teste.com",
                      "phone": "31989877782",
                      "number": "304",
                      "address": "Rua Norberto Mayer",
                      "district": "",
                      "document": "22060575060",
                      "complement": "apt 302",
                      "country_id": "BR",
                      "state_abbr": "MG",
                      "postal_code": "32315100"
                    },
                    "options": {
                      "receipt": false,
                      "reverse": false,
                      "own_hand": false,
                      "platform": "Imprime.AI",
                      "reminder": "Pedido Imprime.AI",
                      "non_commercial": true,
                      "insurance_value": 65.0
                    },
                    "volumes": [
                      { "width": 14.0, "height": 14.0, "length": 12.0, "weight": 0.34 }
                    ],
                    "products": [
                      { "name": "Chaveiro", "quantity": 1, "unitary_value": 65.0 }
                    ]
                  }
                }
                """);

        Address originAddr = new Address();
        originAddr.setId(60L);
        originAddr.setNeighborhood("Eldorado");
        when(addressRepository.findById(60L)).thenReturn(Optional.of(originAddr));

        stubOrderReadyForShipment();

        JsonNode cartResp = objectMapper.readTree("{\"id\":\"me-cart-correios\",\"price\":\"18.90\"}");
        JsonNode cartDetail = objectMapper.readTree("{\"id\":\"me-cart-correios\",\"price\":\"18.90\",\"status\":\"pending\"}");
        JsonNode orderDetail = objectMapper.readTree("{\"id\":\"me-cart-correios\",\"status\":\"released\",\"generated_at\":null}");
        when(melhorEnvioClient.post(eq("/api/v2/me/cart"), any())).thenReturn(cartResp);
        when(melhorEnvioClient.get("/api/v2/me/cart/me-cart-correios")).thenReturn(cartDetail);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/checkout"), any()))
                .thenReturn(objectMapper.readTree("{\"purchase\":{\"status\":\"paid\"}}"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/generate"), any()))
                .thenReturn(objectMapper.readTree("{}"));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-cart-correios")).thenReturn(orderDetail);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any()))
                .thenReturn(objectMapper.readTree("{}"));

        OrderShipmentResponse response = orderShipmentService.generate("order-guid-900");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(melhorEnvioClient).post(eq("/api/v2/me/cart"), payloadCaptor.capture());
        verify(melhorEnvioClient, never()).get(contains("/api/v2/me/shipment/agencies"));

        Map<String, Object> sent = payloadCaptor.getValue();
        assertEquals(1, sent.get("agency"));

        Map<String, Object> from = (Map<String, Object>) sent.get("from");
        assertEquals("Eldorado", from.get("district"));
        assertEquals("apt 302", from.get("complement"));

        Map<String, Object> to = (Map<String, Object>) sent.get("to");
        assertEquals("Coração Eucarístico", to.get("district"));
        assertFalse(to.containsKey("complement"));

        List<Map<String, Object>> volumes = (List<Map<String, Object>>) sent.get("volumes");
        assertEquals(14, volumes.get(0).get("width"));
        assertEquals(14, volumes.get(0).get("height"));
        assertEquals(16, volumes.get(0).get("length"));
        assertEquals(0, new BigDecimal("0.340").compareTo(new BigDecimal(String.valueOf(volumes.get(0).get("weight")))));
        assertEquals("0.340", String.valueOf(volumes.get(0).get("weight")));

        assertNull(response.packages().get(0).lastError());
        assertEquals("GENERATING", response.packages().get(0).status());
    }

    @Test
    void generateRevertsCartPendingToSelectedOn422AndFormatsMelhorEnvioError() {
        stubOrderReadyForShipment();

        String errorJson = """
                {"message":"The given data was invalid.","errors":{"from.document":["CPF invalido"]}}
                """;
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatusCode.valueOf(422),
                "Unprocessable Entity",
                HttpHeaders.EMPTY,
                errorJson.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(melhorEnvioClient.post(eq("/api/v2/me/cart"), any())).thenThrow(ex);

        OrderShipmentResponse response = orderShipmentService.generate("order-guid-900");

        assertEquals(OrderShipmentStatus.SELECTED.name(), response.packages().get(0).status());
        String lastError = response.packages().get(0).lastError();
        assertNotNull(lastError);
        assertTrue(lastError.contains("HTTP 422"));
        assertTrue(lastError.contains("from.document: CPF invalido"));
    }

    @Test
    void generateRevertsCartPendingToSelectedOnMelhorEnvioCartSave500() {
        stubOrderReadyForShipment();

        String errorJson = "{\"error\":\"Houve um erro ao salvar o pedido no carrinho.\"}";
        HttpServerErrorException ex = HttpServerErrorException.create(
                HttpStatusCode.valueOf(500),
                "Internal Server Error",
                HttpHeaders.EMPTY,
                errorJson.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(melhorEnvioClient.post(eq("/api/v2/me/cart"), any())).thenThrow(ex);

        OrderShipmentResponse response = orderShipmentService.generate("order-guid-900");

        assertEquals(OrderShipmentStatus.SELECTED.name(), response.packages().get(0).status());
        assertNotNull(response.packages().get(0).lastError());
        assertTrue(response.packages().get(0).lastError().contains("HTTP 500"));
        assertTrue(response.packages().get(0).lastError().contains("Houve um erro ao salvar o pedido no carrinho."));
    }

    @Test
    void generateKeepsCartPendingOnGeneric500AndTimeout() {
        stubOrderReadyForShipment();

        HttpServerErrorException generic500 = HttpServerErrorException.create(
                HttpStatusCode.valueOf(500),
                "Internal Server Error",
                HttpHeaders.EMPTY,
                "{\"error\":\"Erro inesperado no servidor\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(melhorEnvioClient.post(eq("/api/v2/me/cart"), any())).thenThrow(generic500);

        OrderShipmentResponse response500 = orderShipmentService.generate("order-guid-900");
        assertEquals(OrderShipmentStatus.CART_PENDING.name(), response500.packages().get(0).status());

        when(melhorEnvioClient.post(eq("/api/v2/me/cart"), any()))
                .thenThrow(new IllegalStateException("Connection timed out"));

        OrderShipmentResponse responseTimeout = orderShipmentService.generate("order-guid-900");
        assertEquals(OrderShipmentStatus.CART_PENDING.name(), responseTimeout.packages().get(0).status());
        assertNotNull(responseTimeout.packages().get(0).lastError());
    }

    @Test
    void generateRejectsDuplicateDocumentBeforeCallingMelhorEnvio() {
        order.setShippingQuoteSnapshotJson(order.getShippingQuoteSnapshotJson().replace("98765432100", "12345678909"));
        stubOrderReadyForShipment();

        OrderShipmentResponse response = orderShipmentService.generate("order-guid-900");

        verify(melhorEnvioClient, never()).post(eq("/api/v2/me/cart"), any());
        assertEquals(OrderShipmentStatus.SELECTED.name(), response.packages().get(0).status());
        assertNotNull(response.packages().get(0).lastError());
        assertTrue(response.packages().get(0).lastError().contains("mesmo CPF/CNPJ"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateResolvesAgencyForJadlogBeforeCartPost() {
        order.setShippingQuoteSnapshotJson("""
                {
                  "quote": { "id": 4, "name": ".Package", "company": { "id": 2, "name": "Jadlog" } },
                  "cart": {
                    "from": {
                      "name": "Lojinha Teste",
                      "phone": "31988887777",
                      "document": "12345678909",
                      "city": "Contagem",
                      "state_abbr": "MG",
                      "postal_code": "32315100"
                    },
                    "to": {
                      "name": "Gabriel Amorim",
                      "phone": "31999991111",
                      "document": "98765432100",
                      "city": "Belo Horizonte",
                      "state_abbr": "MG",
                      "postal_code": "30535510"
                    },
                    "products": [
                      { "name": "Chaveiro", "quantity": 1, "unitary_value": 65.00 }
                    ],
                    "volumes": [
                      { "width": 12, "height": 10, "length": 16, "weight": 0.400 }
                    ],
                    "options": {
                      "non_commercial": true,
                      "insurance_value": 65.00
                    }
                  }
                }
                """);
        stubOrderReadyForShipment();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        when(melhorEnvioClient.get(eq("/api/v2/me/shipment/agencies?company=2&country=BR&state=MG&city=Contagem")))
                .thenReturn(objectMapper.readTree("[{\"id\":99,\"status\":\"unavailable\"},{\"id\":42,\"status\":\"available\"}]"));
        when(melhorEnvioClient.post(eq("/api/v2/me/cart"), any()))
                .thenReturn(objectMapper.readTree("{\"id\":\"me-cart-jadlog\",\"price\":\"18.90\"}"));
        when(melhorEnvioClient.get("/api/v2/me/cart/me-cart-jadlog"))
                .thenReturn(objectMapper.readTree("{\"id\":\"me-cart-jadlog\",\"price\":\"18.90\",\"status\":\"pending\"}"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/checkout"), any()))
                .thenReturn(objectMapper.readTree("{\"purchase\":{\"status\":\"paid\"}}"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/generate"), any()))
                .thenReturn(objectMapper.readTree("{}"));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-cart-jadlog"))
                .thenReturn(objectMapper.readTree("{\"id\":\"me-cart-jadlog\",\"status\":\"released\",\"generated_at\":null}"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any()))
                .thenReturn(objectMapper.readTree("{}"));

        OrderShipmentResponse response = orderShipmentService.generate("order-guid-900");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(melhorEnvioClient).post(eq("/api/v2/me/cart"), payloadCaptor.capture());
        assertEquals(42, payloadCaptor.getValue().get("agency"));
        assertEquals("GENERATING", response.packages().get(0).status());
    }

    @Test
    void getSyncsGeneratingShipmentFromOrdersEndpointImmediatelyEvenWithin30Seconds() {
        OrderShipment generatingShipment = new OrderShipment();
        generatingShipment.setGuid("ship-guid-123");
        generatingShipment.setOrderId(900L);
        generatingShipment.setServiceId(2);
        generatingShipment.setCarrierName("Correios");
        generatingShipment.setServiceName("SEDEX");
        generatingShipment.setExternalOrderId("me-order-123");
        generatingShipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        generatingShipment.setPackageNumber(1);
        generatingShipment.setUpdateDt(Instant.now());

        OrderStatus readyStatus = new OrderStatus();
        readyStatus.setStatusCd(OrderStatusCd.READY_TO_SHIP);

        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(generatingShipment));
        when(orderStatusService.findLatest(order)).thenReturn(readyStatus);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-order-123")).thenReturn(objectMapper.readTree(
                "{\"id\":\"me-order-123\",\"status\":\"released\",\"generated_at\":\"2026-11-22 08:45:03\",\"tracking\":\"AA123456789BR\"}"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        verify(melhorEnvioClient).get("/api/v2/me/orders/me-order-123");
        verify(melhorEnvioClient, never()).get("/api/v2/me/cart/me-order-123");
        assertEquals(OrderShipmentStatus.LABEL_READY.name(), response.packages().get(0).status());
        assertNotNull(response.packages().get(0).labelExpiresAt());
        assertEquals("AA123456789BR", response.trackingCode());
        verify(orderStatusService).move(order, OrderStatusCd.IN_TRANSIT);
    }

    @Test
    void getParsesKeyedTrackingObjectResponseWhenOrdersEndpointHasNoTrackingYet() {
        OrderShipment generatingShipment = new OrderShipment();
        generatingShipment.setGuid("ship-guid-456");
        generatingShipment.setOrderId(900L);
        generatingShipment.setServiceId(2);
        generatingShipment.setCarrierName("Correios");
        generatingShipment.setServiceName("SEDEX");
        generatingShipment.setExternalOrderId("me-order-456");
        generatingShipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        generatingShipment.setPackageNumber(1);
        generatingShipment.setUpdateDt(Instant.now());

        OrderStatus readyStatus = new OrderStatus();
        readyStatus.setStatusCd(OrderStatusCd.READY_TO_SHIP);

        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(generatingShipment));
        when(orderStatusService.findLatest(order)).thenReturn(readyStatus);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-order-456")).thenReturn(objectMapper.readTree(
                "{\"id\":\"me-order-456\",\"status\":\"released\",\"generated_at\":\"2026-11-22 08:45:03\",\"tracking\":null}"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any())).thenReturn(objectMapper.readTree(
                "{\"me-order-456\":{\"id\":\"me-order-456\",\"status\":\"released\",\"tracking\":\"BR987654321ME\",\"generated_at\":\"2026-11-22 08:45:03\"}}"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        assertEquals(OrderShipmentStatus.LABEL_READY.name(), response.packages().get(0).status());
        assertEquals("BR987654321ME", response.trackingCode());
        verify(orderStatusService).move(order, OrderStatusCd.IN_TRANSIT);
    }

    @Test
    void syncFallsBackToCartEndpointWhenOrdersReturns404() {
        OrderShipment generatingShipment = new OrderShipment();
        generatingShipment.setGuid("ship-guid-404");
        generatingShipment.setOrderId(900L);
        generatingShipment.setServiceId(2);
        generatingShipment.setCarrierName("Correios");
        generatingShipment.setServiceName("SEDEX");
        generatingShipment.setExternalOrderId("me-order-404");
        generatingShipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        generatingShipment.setPackageNumber(1);
        generatingShipment.setUpdateDt(Instant.now());

        OrderStatus readyStatus = new OrderStatus();
        readyStatus.setStatusCd(OrderStatusCd.READY_TO_SHIP);

        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatusCode.valueOf(404),
                "Not Found",
                HttpHeaders.EMPTY,
                "{}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(generatingShipment));
        when(orderStatusService.findLatest(order)).thenReturn(readyStatus);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-order-404")).thenThrow(notFound);
        when(melhorEnvioClient.get("/api/v2/me/cart/me-order-404")).thenReturn(objectMapper.readTree(
                "{\"id\":\"me-order-404\",\"status\":\"generated\",\"generated_at\":\"2026-11-22 08:45:03\",\"melhorenvio_tracking\":\"ME404TRACKBR\"}"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        verify(melhorEnvioClient).get("/api/v2/me/orders/me-order-404");
        verify(melhorEnvioClient).get("/api/v2/me/cart/me-order-404");
        assertEquals(OrderShipmentStatus.LABEL_READY.name(), response.packages().get(0).status());
        assertEquals("ME404TRACKBR", response.trackingCode());
        verify(orderStatusService).move(order, OrderStatusCd.IN_TRANSIT);
    }

    @Test
    void getFallsBackToTrackingWhenOrdersEndpointReturns403Forbidden() {
        OrderShipment generatingShipment = new OrderShipment();
        generatingShipment.setGuid("ship-guid-403");
        generatingShipment.setOrderId(900L);
        generatingShipment.setServiceId(2);
        generatingShipment.setCarrierName("Correios");
        generatingShipment.setServiceName("SEDEX");
        generatingShipment.setExternalOrderId("me-order-403");
        generatingShipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        generatingShipment.setPackageNumber(1);
        generatingShipment.setUpdateDt(Instant.now());
        generatingShipment.setLastError("HTTP 403 anterior");

        OrderStatus readyStatus = new OrderStatus();
        readyStatus.setStatusCd(OrderStatusCd.READY_TO_SHIP);

        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatusCode.valueOf(403),
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"message\":\"This action is unauthorized.\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(generatingShipment));
        when(orderStatusService.findLatest(order)).thenReturn(readyStatus);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-order-403")).thenThrow(forbidden);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any())).thenReturn(objectMapper.readTree(
                "{\"me-order-403\":{\"id\":\"me-order-403\",\"status\":\"released\",\"generated_at\":\"2026-11-22 08:45:03\",\"tracking\":\"BR403FALLBACK\"}}"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        assertEquals(OrderShipmentStatus.LABEL_READY.name(), response.packages().get(0).status());
        assertEquals("BR403FALLBACK", response.trackingCode());
        assertNull(response.packages().get(0).lastError());
        verify(orderStatusService).move(order, OrderStatusCd.IN_TRANSIT);
    }

    @Test
    void getMovesInTransitOrderToCompletedWhenTrackingStatusIsDelivered() {
        OrderShipment inTransitShipment = new OrderShipment();
        inTransitShipment.setGuid("ship-guid-deliv");
        inTransitShipment.setOrderId(900L);
        inTransitShipment.setServiceId(2);
        inTransitShipment.setCarrierName("Correios");
        inTransitShipment.setServiceName("SEDEX");
        inTransitShipment.setExternalOrderId("me-order-deliv");
        inTransitShipment.setShipmentStatus(OrderShipmentStatus.LABEL_READY);
        inTransitShipment.setTrackingCode("BR111222333BR");
        inTransitShipment.setPackageNumber(1);
        inTransitShipment.setUpdateDt(Instant.now().minusSeconds(120));

        OrderStatus inTransitStatus = new OrderStatus();
        inTransitStatus.setStatusCd(OrderStatusCd.IN_TRANSIT);

        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(inTransitShipment));
        when(orderStatusService.findLatest(order)).thenReturn(inTransitStatus);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-order-deliv")).thenReturn(objectMapper.readTree(
                "{\"id\":\"me-order-deliv\",\"status\":\"delivered\",\"generated_at\":\"2026-11-20 08:45:03\",\"tracking\":\"BR111222333BR\"}"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        assertEquals(OrderShipmentStatus.DELIVERED.name(), response.packages().get(0).status());
        assertEquals("delivered", response.trackingStatus());
        assertEquals("delivered", order.getTrackingStatus());
        verify(orderStatusService).move(order, OrderStatusCd.COMPLETED);
    }

    @Test
    void getMovesReadyToShipOrderThroughInTransitToCompletedWhenDelivered() {
        OrderShipment shipment = new OrderShipment();
        shipment.setGuid("ship-guid-deliv-ready");
        shipment.setOrderId(900L);
        shipment.setServiceId(2);
        shipment.setCarrierName("Correios");
        shipment.setServiceName("SEDEX");
        shipment.setExternalOrderId("me-order-deliv-ready");
        shipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        shipment.setPackageNumber(1);
        shipment.setUpdateDt(Instant.now());

        OrderStatus readyStatus = new OrderStatus();
        readyStatus.setStatusCd(OrderStatusCd.READY_TO_SHIP);

        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(shipment));
        when(orderStatusService.findLatest(order)).thenReturn(readyStatus);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-order-deliv-ready")).thenReturn(objectMapper.readTree(
                "{\"id\":\"me-order-deliv-ready\",\"status\":\"posted\",\"delivered_at\":\"2026-11-22 10:00:00\",\"tracking\":\"BR999888777BR\"}"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        assertEquals(OrderShipmentStatus.DELIVERED.name(), response.packages().get(0).status());
        verify(orderStatusService).move(order, OrderStatusCd.IN_TRANSIT);
        verify(orderStatusService).move(order, OrderStatusCd.COMPLETED);
    }

    @Test
    void getDoesNotMoveOrderAgainWhenAlreadyCompleted() {
        OrderShipment deliveredShipment = new OrderShipment();
        deliveredShipment.setGuid("ship-guid-done");
        deliveredShipment.setOrderId(900L);
        deliveredShipment.setExternalOrderId("me-order-done");
        deliveredShipment.setShipmentStatus(OrderShipmentStatus.DELIVERED);
        deliveredShipment.setTrackingCode("BR000111222BR");
        deliveredShipment.setPackageNumber(1);
        deliveredShipment.setUpdateDt(Instant.now().minusSeconds(600));

        order.setTrackingCode("BR000111222BR");
        order.setTrackingStatus("delivered");

        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(deliveredShipment));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        assertEquals(OrderShipmentStatus.DELIVERED.name(), response.packages().get(0).status());
        verifyNoInteractions(melhorEnvioClient);
        verify(orderStatusService, never()).move(any(), any());
    }

    @Test
    void getReportsPermissionErrorWhenBothOrdersAndTrackingReturn403() {
        OrderShipment generatingShipment = new OrderShipment();
        generatingShipment.setGuid("ship-guid-403b");
        generatingShipment.setOrderId(900L);
        generatingShipment.setServiceId(2);
        generatingShipment.setCarrierName("Correios");
        generatingShipment.setServiceName("SEDEX");
        generatingShipment.setExternalOrderId("me-order-403b");
        generatingShipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        generatingShipment.setPackageNumber(1);
        generatingShipment.setUpdateDt(Instant.now());

        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatusCode.valueOf(403),
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"message\":\"This action is unauthorized.\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(generatingShipment));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/me-order-403b")).thenThrow(forbidden);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any())).thenThrow(forbidden);

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        assertEquals(OrderShipmentStatus.GENERATING.name(), response.packages().get(0).status());
        String lastError = response.packages().get(0).lastError();
        assertNotNull(lastError);
        assertTrue(lastError.contains("HTTP 403"));
        assertTrue(lastError.contains("orders-read"));
        assertTrue(lastError.contains("shipping-tracking"));
    }

    @Test
    void downloadLabelFetchesSignedPdfFromImprimirEndpointAndValidatesPdfHeader() {
        OrderShipment readyShipment = new OrderShipment();
        readyShipment.setGuid("ship-guid-pdf");
        readyShipment.setOrderId(900L);
        readyShipment.setExternalOrderId("me-order-pdf");
        readyShipment.setShipmentStatus(OrderShipmentStatus.LABEL_READY);
        readyShipment.setPackageNumber(1);
        readyShipment.setUpdateDt(Instant.now());

        String s3Url = "https://me-0047-sandbox.s3.amazonaws.com/pdf/me-order-pdf.pdf?X-Amz-Signature=abc";
        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByGuidAndOrderIdAndStatus("ship-guid-pdf", 900L, StatusCd.ACTIVE))
                .thenReturn(Optional.of(readyShipment));
        when(melhorEnvioClient.get("/api/v2/me/imprimir/pdf/me-order-pdf"))
                .thenReturn(objectMapper.readTree("[\"" + s3Url + "\"]"));
        when(melhorEnvioClient.download(s3Url))
                .thenReturn("%PDF-1.4\n%Valid\n%%EOF".getBytes(StandardCharsets.UTF_8));

        OrderFileContent content = orderShipmentService.downloadLabel("order-guid-900", "ship-guid-pdf");

        verify(melhorEnvioClient, never()).post(eq("/api/v2/me/shipment/print"), any());
        assertEquals("application/pdf", content.contentType());
        assertEquals("etiqueta-PED-900.pdf", content.fileName());
        assertTrue(new String(content.bytes(), StandardCharsets.UTF_8).startsWith("%PDF"));
    }

    @Test
    void downloadLabelRejectsNonPdfHtmlPayload() {
        OrderShipment readyShipment = new OrderShipment();
        readyShipment.setGuid("ship-guid-html");
        readyShipment.setOrderId(900L);
        readyShipment.setExternalOrderId("me-order-html");
        readyShipment.setShipmentStatus(OrderShipmentStatus.LABEL_READY);
        readyShipment.setPackageNumber(1);
        readyShipment.setUpdateDt(Instant.now());

        String url = "https://sandbox.melhorenvio.com.br/portal/imprimir/X0mit53HV7Kr";
        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByGuidAndOrderIdAndStatus("ship-guid-html", 900L, StatusCd.ACTIVE))
                .thenReturn(Optional.of(readyShipment));
        when(melhorEnvioClient.get("/api/v2/me/imprimir/pdf/me-order-html"))
                .thenReturn(objectMapper.readTree("[\"" + url + "\"]"));
        when(melhorEnvioClient.download(url))
                .thenReturn("<!DOCTYPE html><html>portal</html>".getBytes(StandardCharsets.UTF_8));

        assertThrows(ConflictException.class,
                () -> orderShipmentService.downloadLabel("order-guid-900", "ship-guid-html"));
    }

    @Test
    void getRetriggersGenerateWhenOrderIsReleasedPaidButGeneratedAtIsNull() {
        String extId = "a348fabc-a7a3-4dee-9937-8abf2d8ef633";
        OrderShipment generatingShipment = new OrderShipment();
        generatingShipment.setGuid("8a151009-2de9-4af0-85b4-4f58fd0b671e");
        generatingShipment.setOrderId(900L);
        generatingShipment.setServiceId(2);
        generatingShipment.setCarrierName("Correios");
        generatingShipment.setServiceName("SEDEX");
        generatingShipment.setExternalOrderId(extId);
        generatingShipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        generatingShipment.setPackageNumber(1);
        generatingShipment.setUpdateDt(Instant.now());
        generatingShipment.setLastError("Erro anterior");

        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatusCode.valueOf(403),
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"message\":\"This action is unauthorized.\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(generatingShipment));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/" + extId)).thenThrow(forbidden);
        when(melhorEnvioClient.get("/api/v2/me/imprimir/pdf/" + extId)).thenReturn(objectMapper.readTree("[]"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any())).thenReturn(objectMapper.readTree(
                "{\"" + extId + "\":{\"id\":\"" + extId + "\",\"status\":\"released\",\"paid_at\":\"2026-11-22 09:06:37\",\"generated_at\":null}}"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/generate"), any())).thenReturn(objectMapper.readTree(
                "{\"" + extId + "\":{\"status\":true,\"message\":\"Envio gerado com sucesso\"}}"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        verify(melhorEnvioClient).post(eq("/api/v2/me/shipment/generate"), eq(Map.of("orders", List.of(extId))));
        assertEquals(OrderShipmentStatus.GENERATING.name(), response.packages().get(0).status());
        assertNull(response.packages().get(0).lastError());
    }

    @Test
    void getMarksLabelReadyWhenImprimirPdfAlreadyReturnsUrlEvenIfTrackingHasNullGeneratedAt() {
        String extId = "a348fabc-a7a3-4dee-9937-8abf2d8ef633";
        OrderShipment generatingShipment = new OrderShipment();
        generatingShipment.setGuid("8a151009-2de9-4af0-85b4-4f58fd0b671e");
        generatingShipment.setOrderId(900L);
        generatingShipment.setServiceId(2);
        generatingShipment.setCarrierName("Correios");
        generatingShipment.setServiceName("SEDEX");
        generatingShipment.setExternalOrderId(extId);
        generatingShipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        generatingShipment.setPackageNumber(1);
        generatingShipment.setUpdateDt(Instant.now());
        generatingShipment.setLastError("Erro anterior");

        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatusCode.valueOf(403),
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"message\":\"This action is unauthorized.\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(generatingShipment));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/" + extId)).thenThrow(forbidden);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any())).thenReturn(objectMapper.readTree(
                "{\"" + extId + "\":{\"id\":\"" + extId + "\",\"status\":\"released\",\"paid_at\":\"2026-11-22 09:06:37\",\"generated_at\":null}}"));
        when(melhorEnvioClient.get("/api/v2/me/imprimir/pdf/" + extId)).thenReturn(objectMapper.readTree(
                "[\"https://me-0047-sandbox.s3.amazonaws.com/pdf/ready.pdf\"]"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        verify(melhorEnvioClient, never()).post(eq("/api/v2/me/shipment/generate"), any());
        assertEquals(OrderShipmentStatus.LABEL_READY.name(), response.packages().get(0).status());
        assertNotNull(response.packages().get(0).labelExpiresAt());
        assertNull(response.packages().get(0).lastError());
    }

    @Test
    void getMarksLabelReadyWhenRegenerateReportsAlreadyGenerated() {
        String extId = "a348fabc-a7a3-4dee-9937-8abf2d8ef633";
        OrderShipment generatingShipment = new OrderShipment();
        generatingShipment.setGuid("8a151009-2de9-4af0-85b4-4f58fd0b671e");
        generatingShipment.setOrderId(900L);
        generatingShipment.setServiceId(2);
        generatingShipment.setCarrierName("Correios");
        generatingShipment.setServiceName("SEDEX");
        generatingShipment.setExternalOrderId(extId);
        generatingShipment.setShipmentStatus(OrderShipmentStatus.GENERATING);
        generatingShipment.setPackageNumber(1);
        generatingShipment.setUpdateDt(Instant.now());

        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatusCode.valueOf(403),
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"message\":\"This action is unauthorized.\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of(generatingShipment));
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(melhorEnvioClient.get("/api/v2/me/orders/" + extId)).thenThrow(forbidden);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/tracking"), any())).thenReturn(objectMapper.readTree(
                "{\"" + extId + "\":{\"id\":\"" + extId + "\",\"status\":\"released\",\"paid_at\":\"2026-11-22 09:06:37\",\"generated_at\":null}}"));
        when(melhorEnvioClient.get("/api/v2/me/imprimir/pdf/" + extId)).thenReturn(objectMapper.readTree("[]"));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/generate"), any())).thenReturn(objectMapper.readTree(
                "{\"" + extId + "\":{\"status\":false,\"message\":\"O envio ja está gerado\"}}"));

        OrderShipmentResponse response = orderShipmentService.get("order-guid-900");

        assertEquals(OrderShipmentStatus.LABEL_READY.name(), response.packages().get(0).status());
        assertNotNull(response.packages().get(0).labelExpiresAt());
        assertNull(response.packages().get(0).lastError());
    }

    @Test
    void melhorEnvioClientAllowsS3PdfUrlsAndRejectsUntrustedHosts() {
        assertTrue(MelhorEnvioClient.isAllowedS3PdfUri(
                URI.create("https://me-0047-sandbox.s3.amazonaws.com/pdf/a348f222.pdf?X-Amz-Signature=123")));
        assertTrue(MelhorEnvioClient.isMelhorEnvioHost(
                URI.create("https://sandbox.melhorenvio.com.br/portal/imprimir/abc")));
        assertFalse(MelhorEnvioClient.isAllowedS3PdfUri(
                URI.create("https://evil.example.com/pdf/a.pdf")));
        assertFalse(MelhorEnvioClient.isAllowedS3PdfUri(
                URI.create("http://me-0047-sandbox.s3.amazonaws.com/pdf/a.pdf")));
    }

    @Test
    void calculateNormalizesBlankNeighborhoodFullStateSmallDimensionsAndUsesQuotePackages() {
        ServiceContext.getContext().setUser(buyerUser);

        Address dest = new Address();
        dest.setId(50L);
        dest.setGuid("addr-dest-50");
        dest.setOwnerType(EntityType.USER);
        dest.setOwnerId(10L);
        dest.setStatus(StatusCd.ACTIVE);
        dest.setZipCode("32315-100");
        dest.setAddressLine1("Rua Norberto Mayer, 302");
        dest.setAddressLine2("   ");
        dest.setNeighborhood("Eldorado");
        dest.setCity("Contagem");
        dest.setState("MG");

        Address origin = new Address();
        origin.setId(60L);
        origin.setOwnerType(EntityType.COMPANY);
        origin.setOwnerId(200L);
        origin.setStatus(StatusCd.ACTIVE);
        origin.setZipCode("30140-071");
        origin.setAddressLine1("Av Brasil, 100");
        origin.setNeighborhood("   ");
        origin.setCity("Belo Horizonte");
        origin.setState("Minas Gerais");

        Company company = new Company();
        company.setId(200L);
        company.setGuid("company-200");
        company.setName("Lojinha Teste");
        company.setOwnerId(20L);
        company.setAddressId(60L);

        Product product = new Product();
        product.setId(1L);
        product.setGuid("prod-1");
        product.setCompanyId(200L);
        product.setName("Chaveiro");
        product.setMaterial(MaterialType.PETG);
        product.setPrice(new BigDecimal("65.00"));
        product.setWidthMm(new BigDecimal("50"));
        product.setDepthMm(new BigDecimal("50"));
        product.setHeightMm(new BigDecimal("20"));
        product.setWeightGrams(100);

        when(addressRepository.findByGuidAndStatus("addr-dest-50", StatusCd.ACTIVE))
                .thenReturn(Optional.of(dest));
        when(productRepository.findByGuidAndStatus("prod-1", StatusCd.ACTIVE))
                .thenReturn(Optional.of(product));
        when(companyRepository.findById(200L)).thenReturn(Optional.of(company));
        when(addressRepository.findById(60L)).thenReturn(Optional.of(origin));
        when(userRepository.findById(20L)).thenReturn(Optional.of(makerUser));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/calculate"), any()))
                .thenReturn(objectMapper.readTree("""
                        [
                          {
                            "id": 2,
                            "name": "SEDEX",
                            "price": "24.50",
                            "delivery_time": 2,
                            "company": { "id": 1, "name": "Correios" }
                          },
                          {
                            "id": 4,
                            "name": ".Package",
                            "price": "18.90",
                            "delivery_time": 4,
                            "company": { "id": 2, "name": "Jadlog" },
                            "packages": [
                              {
                                "weight": "0.45",
                                "dimensions": { "width": 12, "height": 5, "length": 18 }
                              }
                            ]
                          }
                        ]
                        """));
        when(quoteRepository.save(any(OrderShippingQuote.class))).thenAnswer(inv -> inv.getArgument(0));

        CalculateShippingQuotesRequest request = new CalculateShippingQuotesRequest(
                List.of(new CheckoutItemRequest("prod-1", "Chaveiro", "Lojinha Teste", "PETG", null, new BigDecimal("65.00"), 1)),
                "addr-dest-50",
                null
        );

        shippingQuoteService.calculate(request);

        ArgumentCaptor<OrderShippingQuote> quoteCaptor = ArgumentCaptor.forClass(OrderShippingQuote.class);
        verify(quoteRepository, times(2)).save(quoteCaptor.capture());

        OrderShippingQuote correiosQuote = quoteCaptor.getAllValues().get(0);
        JsonNode correiosCart = objectMapper.readTree(correiosQuote.getSnapshotJson()).path("cart");
        assertEquals("Centro", correiosCart.path("from").path("district").asText());
        assertEquals("MG", correiosCart.path("from").path("state_abbr").asText());
        assertTrue(correiosCart.path("to").path("complement").isMissingNode());
        JsonNode correiosVol = correiosCart.path("volumes").get(0);
        assertEquals(11, correiosVol.path("width").asInt());
        assertEquals(4, correiosVol.path("height").asInt());
        assertEquals(16, correiosVol.path("length").asInt());

        OrderShippingQuote jadlogQuote = quoteCaptor.getAllValues().get(1);
        JsonNode jadlogCart = objectMapper.readTree(jadlogQuote.getSnapshotJson()).path("cart");
        JsonNode jadlogVol = jadlogCart.path("volumes").get(0);
        assertEquals(12, jadlogVol.path("width").asInt());
        assertEquals(5, jadlogVol.path("height").asInt());
        assertEquals(18, jadlogVol.path("length").asInt());
        assertEquals(0, new BigDecimal("0.450").compareTo(new BigDecimal(jadlogVol.path("weight").asText())));
    }

    private void stubOrderReadyForShipment() {
        OrderStatus readyStatus = new OrderStatus();
        readyStatus.setStatusCd(OrderStatusCd.READY_TO_SHIP);

        when(orderRepository.findOrderByGuid("order-guid-900")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(readyStatus);
        when(shipmentRepository.findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(900L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(shipmentRepository.save(any(OrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void calculateBuildsCartSnapshotWithNonCommercialInsuranceValueAndNormalizedPhone() {
        ServiceContext.getContext().setUser(buyerUser);

        Address dest = new Address();
        dest.setId(50L);
        dest.setGuid("addr-dest-50");
        dest.setOwnerType(EntityType.USER);
        dest.setOwnerId(10L);
        dest.setStatus(StatusCd.ACTIVE);
        dest.setZipCode("32315-100");
        dest.setAddressLine1("Rua Norberto Mayer, 302");
        dest.setNeighborhood("Eldorado");
        dest.setCity("Contagem");
        dest.setState("MG");

        Address origin = new Address();
        origin.setId(60L);
        origin.setOwnerType(EntityType.COMPANY);
        origin.setOwnerId(200L);
        origin.setStatus(StatusCd.ACTIVE);
        origin.setZipCode("30140-071");
        origin.setAddressLine1("Av Brasil, 100");
        origin.setNeighborhood("Funcionarios");
        origin.setCity("Belo Horizonte");
        origin.setState("MG");

        Company company = new Company();
        company.setId(200L);
        company.setGuid("company-200");
        company.setName("Lojinha Teste");
        company.setOwnerId(20L);
        company.setAddressId(60L);

        Product product = new Product();
        product.setId(1L);
        product.setGuid("prod-1");
        product.setCompanyId(200L);
        product.setName("Chaveiro");
        product.setMaterial(MaterialType.PETG);
        product.setPrice(new BigDecimal("65.00"));
        product.setWidthMm(new BigDecimal("50"));
        product.setDepthMm(new BigDecimal("50"));
        product.setHeightMm(new BigDecimal("20"));
        product.setWeightGrams(100);

        when(addressRepository.findByGuidAndStatus("addr-dest-50", StatusCd.ACTIVE))
                .thenReturn(Optional.of(dest));
        when(productRepository.findByGuidAndStatus("prod-1", StatusCd.ACTIVE))
                .thenReturn(Optional.of(product));
        when(companyRepository.findById(200L)).thenReturn(Optional.of(company));
        when(addressRepository.findById(60L)).thenReturn(Optional.of(origin));
        when(userRepository.findById(20L)).thenReturn(Optional.of(makerUser));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/calculate"), any()))
                .thenReturn(objectMapper.readTree("[{\"id\":4,\"name\":\".Package\",\"price\":\"18.90\",\"delivery_time\":4,\"company\":{\"name\":\"Jadlog\"}}]"));
        when(quoteRepository.save(any(OrderShippingQuote.class))).thenAnswer(inv -> inv.getArgument(0));

        CalculateShippingQuotesRequest request = new CalculateShippingQuotesRequest(
                List.of(new CheckoutItemRequest("prod-1", "Chaveiro", "Lojinha Teste", "PETG", null, new BigDecimal("65.00"), 2)),
                "addr-dest-50",
                null
        );

        shippingQuoteService.calculate(request);

        ArgumentCaptor<OrderShippingQuote> quoteCaptor = ArgumentCaptor.forClass(OrderShippingQuote.class);
        verify(quoteRepository).save(quoteCaptor.capture());

        JsonNode snap = objectMapper.readTree(quoteCaptor.getValue().getSnapshotJson());
        JsonNode cart = snap.path("cart");
        assertTrue(cart.path("options").path("non_commercial").asBoolean());
        assertFalse(cart.path("options").path("reverse").asBoolean());
        assertEquals(0, new BigDecimal("130.00").compareTo(new BigDecimal(cart.path("options").path("insurance_value").asText())));
        assertTrue(cart.path("volumes").get(0).path("insurance").isMissingNode());

        assertEquals("31989877783", cart.path("from").path("phone").asText());
        assertEquals("12345678909", cart.path("from").path("document").asText());
        assertTrue(cart.path("from").path("company_document").isMissingNode());
        assertTrue(cart.path("from").path("state_register").isMissingNode());

        assertEquals("31912345678", cart.path("to").path("phone").asText());
        assertEquals("98765432100", cart.path("to").path("document").asText());
        assertTrue(cart.path("to").path("state_register").isMissingNode());
    }
}
